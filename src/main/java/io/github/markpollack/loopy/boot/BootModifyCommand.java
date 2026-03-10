package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import io.github.markpollack.loopy.boot.modify.JvmVersionResolver;
import io.github.markpollack.loopy.boot.modify.NativeDetector;
import io.github.markpollack.loopy.boot.modify.PomMutator;
import io.github.markpollack.loopy.boot.modify.RecipeCatalog;
import io.github.markpollack.loopy.boot.modify.RecipeClassifier;
import io.github.markpollack.loopy.boot.modify.SqlDialectDetector;
import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /boot-modify} slash command — interprets natural-language modification intents
 * and applies deterministic POM mutations.
 *
 * <p>
 * Three-tier dispatch:
 * </p>
 * <ol>
 * <li><strong>Keyword shortcuts</strong> — instant, zero LLM cost. Covers the most common
 * operations by pattern-matching the lowercased intent string.</li>
 * <li><strong>LLM recipe classifier</strong> — single cheap turn (Haiku), no tools. Maps
 * free-form text to a named recipe and extracts parameters. Execution is still fully
 * deterministic; the LLM only classifies.</li>
 * <li><strong>Full MiniAgent</strong> — multi-turn with file tools. Used only when the
 * intent is genuinely open-ended or requires multi-step reasoning.</li>
 * </ol>
 *
 * <pre>
 * Usage: /boot-modify &lt;intent&gt;
 *
 * Examples:
 *   /boot-modify set java version 21
 *   /boot-modify clean up the pom
 *   /boot-modify add native image support
 *   /boot-modify add actuator
 *   /boot-modify add spring format enforcement
 *   /boot-modify add multi-arch CI
 *   /boot-modify add basic GitHub Actions workflow
 *   /boot-modify add dependency com.example:my-lib:1.0
 *   /boot-modify remove h2 dependency
 * </pre>
 */
public class BootModifyCommand implements SlashCommand {

	private static final Logger logger = LoggerFactory.getLogger(BootModifyCommand.class);

	private final @Nullable ChatModel chatModel;

	public BootModifyCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "boot-modify";
	}

	@Override
	public String description() {
		return "Apply structural modifications to a Spring Boot project (set java version, add native support, add CI, clean pom, etc.)";
	}

	@Override
	public boolean requiresArguments() {
		return true;
	}

	@Override
	public String execute(String args, CommandContext context) {
		if (args == null || args.isBlank()) {
			return helpText();
		}

		Path workDir = context.workingDirectory();
		Path pomFile = workDir.resolve("pom.xml");
		if (!Files.isRegularFile(pomFile)) {
			return "No pom.xml found in " + workDir + ". Run this command from a Maven project root.";
		}

		String intent = args.trim();
		String lower = intent.toLowerCase();
		PomMutator mutator = new PomMutator(pomFile);

		// Tier 1: Keyword shortcuts — instant, no LLM
		String keywordResult = tryKeywordShortcut(lower, mutator, workDir);
		if (keywordResult != null) {
			return keywordResult;
		}

		// Tier 2: LLM recipe classifier — 1 turn, cheap model, deterministic execution
		if (chatModel != null) {
			String recipeResult = tryRecipeClassification(intent, mutator, workDir, pomFile);
			if (recipeResult != null) {
				return recipeResult;
			}
		}

		// Tier 3: Full MiniAgent — open-ended, multi-turn, file tools
		if (chatModel == null) {
			return "No AI model available. Supported intents (no AI needed):\n" + keywordHelp();
		}
		return runAgentModify(intent, pomFile, workDir);
	}

	// --- Tier 1: Keyword shortcuts ---

	/**
	 * Pattern-match the lowercased intent to a recipe and execute it without any LLM.
	 * @return result string, or {@code null} if no keyword match
	 */
	private static @Nullable String tryKeywordShortcut(String lower, PomMutator mutator, Path workDir) {
		try {
			// SET_JAVA_VERSION
			if (lower.contains("java version") || lower.contains("java 21") || lower.contains("java 17")
					|| lower.contains("java 11") || lower.contains("java 8")) {
				String version = extractJavaVersion(lower);
				if (version != null) {
					return mutator.setJavaVersion(version);
				}
			}

			// CLEAN_POM
			if (lower.contains("clean pom") || lower.contains("clean up") || lower.contains("remove empty")
					|| lower.contains("cleanup") || lower.contains("strip empty")) {
				return mutator.cleanPom();
			}

			// ADD_NATIVE_IMAGE
			if ((lower.contains("add") || lower.contains("enable"))
					&& (lower.contains("native image") || lower.contains("native-maven-plugin")
							|| lower.contains("graalvm native") || lower.contains("native support")
							|| lower.contains("native compilation") || lower.contains("native build"))) {
				return mutator.addPlugin("org.graalvm.buildtools", "native-maven-plugin", null);
			}

			// REMOVE_NATIVE_IMAGE
			if ((lower.contains("remove") || lower.contains("delete") || lower.contains("disable"))
					&& (lower.contains("native") && (lower.contains("plugin") || lower.contains("image")
							|| lower.contains("maven") || lower.contains("graalvm")))) {
				return mutator.removePlugin("org.graalvm.buildtools", "native-maven-plugin");
			}

			// ADD_SPRING_FORMAT
			if (lower.contains("spring format") || lower.contains("javaformat") || lower.contains("java format")
					|| lower.contains("spring-javaformat") || lower.contains("format enforcement")) {
				return mutator.addPlugin("io.spring.javaformat", "spring-javaformat-maven-plugin", null);
			}

			// ADD_ACTUATOR
			if (lower.contains("actuator") || (lower.contains("health") && lower.contains("endpoint"))
					|| lower.contains("management endpoint") || lower.contains("micrometer")) {
				return mutator.addDependency("org.springframework.boot", "spring-boot-starter-actuator", null, null);
			}

			// ADD_SECURITY
			if ((lower.contains("spring security") || lower.contains("security starter"))
					|| (lower.contains("add security") && !lower.contains("github"))) {
				return mutator.addDependency("org.springframework.boot", "spring-boot-starter-security", null, null);
			}

			// ADD_MULTI_ARCH_CI — check before ADD_BASIC_CI (multi-arch is more specific)
			if (lower.contains("multi-arch") || lower.contains("multiarch") || lower.contains("multi arch")
					|| lower.contains("arm64") || lower.contains("cross-platform native")) {
				return RecipeCatalog.execute("ADD_MULTI_ARCH_CI", java.util.Map.of(), mutator, workDir);
			}

			// ADD_BASIC_CI
			if ((lower.contains("github actions") || lower.contains("github workflow") || lower.contains("add ci")
					|| lower.contains("basic ci") || lower.contains("maven ci")) && !lower.contains("native")) {
				return RecipeCatalog.execute("ADD_BASIC_CI", java.util.Map.of(), mutator, workDir);
			}
		}
		catch (Exception ex) {
			logger.error("Keyword shortcut failed: {}", ex.getMessage(), ex);
			return "Error applying modification: " + ex.getMessage();
		}
		return null;
	}

	/**
	 * Extract Java version number from lowercased intent (e.g. "java version 21" → "21").
	 */
	private static @Nullable String extractJavaVersion(String lower) {
		String[] versions = { "25", "24", "23", "22", "21", "17", "11", "8" };
		for (String v : versions) {
			if (lower.contains(" " + v) || lower.contains("=" + v) || lower.contains(":" + v)) {
				return v;
			}
		}
		return null;
	}

	// --- Tier 2: LLM recipe classification ---

	/**
	 * Use a cheap LLM call (Haiku, 1 turn, no tools) to classify the intent to a recipe,
	 * then execute that recipe deterministically.
	 * @return result string, or {@code null} if classifier returned "none"
	 */
	private @Nullable String tryRecipeClassification(String intent, PomMutator mutator, Path workDir, Path pomFile) {
		String projectContext = buildProjectContext(pomFile);
		var classifier = new RecipeClassifier(chatModel);
		var result = classifier.classify(intent, projectContext);
		if (!result.matched()) {
			return null;
		}
		try {
			logger.info("Classified '{}' → {}", intent, result.recipeName());
			return RecipeCatalog.execute(result.recipeName(), result.params(), mutator, workDir);
		}
		catch (Exception ex) {
			logger.warn("Recipe execution failed for {}: {}", result.recipeName(), ex.getMessage());
			return "Recipe " + result.recipeName() + " failed: " + ex.getMessage();
		}
	}

	// --- Tier 3: Full MiniAgent ---

	private String runAgentModify(String intent, Path pomFile, Path workDir) {
		String javaVersion = JvmVersionResolver.resolve(pomFile);
		String sqlDialect = SqlDialectDetector.detect(pomFile);
		boolean isNative = NativeDetector.isNative(pomFile);
		String projectAnalysis = readFileIfExists(workDir.resolve("PROJECT-ANALYSIS.md"));

		String task = buildModifyTask(intent, javaVersion, sqlDialect, isNative, projectAnalysis);

		try {
			MiniAgent agent = MiniAgent.builder()
				.config(MiniAgentConfig.builder().workingDirectory(workDir).maxTurns(5).build())
				.model(chatModel)
				.build();
			var result = agent.run(task);
			if (result.output() != null) {
				return result.output();
			}
			return "[" + result.status() + "]";
		}
		catch (Exception ex) {
			logger.warn("Full agent failed for boot-modify: {}", ex.getMessage());
			return "Agent failed: " + ex.getMessage()
					+ "\nTry a more specific intent like: /boot-modify set java version 21";
		}
	}

	private static String buildModifyTask(String intent, @Nullable String javaVersion, @Nullable String sqlDialect,
			boolean isNative, @Nullable String projectAnalysis) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are helping modify a Spring Boot project's pom.xml.\n\n");
		sb.append("User intent: ").append(intent).append("\n\n");
		sb.append("Current project state:\n");
		sb.append("- Java version: ").append(javaVersion != null ? javaVersion : "unknown").append("\n");
		sb.append("- SQL dialect: ").append(sqlDialect != null ? sqlDialect : "none detected").append("\n");
		sb.append("- GraalVM native: ").append(isNative ? "yes (native-maven-plugin present)" : "no").append("\n");
		if (isNative) {
			sb.append("  (NOTE: GraalVM JDK should be used when native-maven-plugin is present)\n");
		}
		if (projectAnalysis != null) {
			sb.append("\nProject analysis:\n").append(projectAnalysis, 0, Math.min(projectAnalysis.length(), 2000));
		}
		sb.append("\nApply the requested modification to pom.xml using the available file tools.");
		sb.append(
				"\nUse the MavenXpp3 object model approach when editing — do NOT produce raw XML string replacements.");
		sb.append("\nExplain what you changed after completing the modification.");
		return sb.toString();
	}

	// --- Helpers ---

	private static String buildProjectContext(Path pomFile) {
		String javaVersion = JvmVersionResolver.resolve(pomFile);
		String sqlDialect = SqlDialectDetector.detect(pomFile);
		boolean isNative = NativeDetector.isNative(pomFile);
		return "Java version: " + (javaVersion != null ? javaVersion : "unknown") + ", SQL dialect: "
				+ (sqlDialect != null ? sqlDialect : "none") + ", GraalVM native: " + (isNative ? "yes" : "no");
	}

	private static @Nullable String readFileIfExists(Path path) {
		try {
			if (Files.isRegularFile(path)) {
				return Files.readString(path);
			}
		}
		catch (IOException ex) {
			logger.debug("Could not read {}: {}", path, ex.getMessage());
		}
		return null;
	}

	private static String helpText() {
		return """
				Usage: /boot-modify <intent>

				Keyword shortcuts (no AI needed):
				  /boot-modify set java version 21
				  /boot-modify clean pom
				  /boot-modify add native image support
				  /boot-modify remove native image
				  /boot-modify add spring format enforcement
				  /boot-modify add actuator
				  /boot-modify add security
				  /boot-modify add multi-arch CI
				  /boot-modify add basic CI workflow

				AI-classified (deterministic execution):
				  /boot-modify add dependency com.example:my-lib:1.0
				  /boot-modify remove h2 dependency
				  /boot-modify I need health check endpoints
				  /boot-modify please make this project build for ARM

				AI-driven (full agent, open-ended):
				  /boot-modify add native image support with custom build args
				  /boot-modify configure multi-module build""";
	}

	private static String keywordHelp() {
		return """
				/boot-modify set java version 21
				/boot-modify clean pom
				/boot-modify add native image support
				/boot-modify add spring format enforcement
				/boot-modify add actuator
				/boot-modify add security
				/boot-modify add multi-arch CI
				/boot-modify add basic CI workflow""";
	}

}
