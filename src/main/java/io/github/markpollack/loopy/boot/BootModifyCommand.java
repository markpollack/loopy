package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import io.github.markpollack.loopy.boot.modify.JvmVersionResolver;
import io.github.markpollack.loopy.boot.modify.NativeDetector;
import io.github.markpollack.loopy.boot.modify.PomMutator;
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
 * One command handles all structural modification intents. The bounded MiniAgent
 * interprets the intent and selects which deterministic operation to run.
 * </p>
 *
 * <p>
 * Deterministic operations (no LLM needed):
 * <ul>
 * <li>{@code set java version <N>} or {@code java version to <N>}</li>
 * <li>{@code clean pom} or {@code clean up the pom}</li>
 * </ul>
 * Other intents are routed through the LLM agent.
 * </p>
 *
 * <pre>
 * Usage: /boot-modify &lt;intent&gt;
 *
 * Examples:
 *   /boot-modify set java version 21
 *   /boot-modify clean up the pom
 *   /boot-modify add native image support
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
		return "Apply structural modifications to a Spring Boot project (set java version, clean pom, etc.)";
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

		// Try deterministic shortcuts first (no LLM needed)
		String deterministicResult = tryDeterministicOperation(args.trim(), pomFile);
		if (deterministicResult != null) {
			return deterministicResult;
		}

		// Fall through to bounded LLM agent
		if (chatModel == null) {
			return "No AI model available. Supported deterministic intents:\n"
					+ "  /boot-modify set java version <N>  — sets java.version property\n"
					+ "  /boot-modify clean pom             — removes empty/null POM fields";
		}

		return runAgentModify(args.trim(), pomFile, workDir);
	}

	/**
	 * Try to execute deterministic operations without LLM.
	 * @return result string, or {@code null} if no deterministic match
	 */
	private static @Nullable String tryDeterministicOperation(String intent, Path pomFile) {
		String lower = intent.toLowerCase();
		PomMutator mutator = new PomMutator(pomFile);

		// "set java version 21" / "java version to 21" / "java version 21"
		if (lower.contains("java version") || lower.contains("java 21") || lower.contains("java 17")
				|| lower.contains("java 11")) {
			String version = extractJavaVersion(lower);
			if (version != null) {
				try {
					return mutator.setJavaVersion(version);
				}
				catch (Exception ex) {
					logger.error("Failed to set java version: {}", ex.getMessage(), ex);
					return "Error setting Java version: " + ex.getMessage();
				}
			}
		}

		// "clean pom" / "clean up the pom" / "remove empty tags"
		if (lower.contains("clean pom") || lower.contains("clean up") || lower.contains("remove empty")
				|| lower.contains("cleanup")) {
			try {
				return mutator.cleanPom();
			}
			catch (Exception ex) {
				logger.error("Failed to clean pom: {}", ex.getMessage(), ex);
				return "Error cleaning pom: " + ex.getMessage();
			}
		}

		return null;
	}

	/**
	 * Extract a Java version number from the intent string (e.g., "java version 21" →
	 * "21").
	 */
	private static @Nullable String extractJavaVersion(String lower) {
		String[] versions = { "25", "24", "23", "22", "21", "17", "11", "8" };
		for (String v : versions) {
			if (lower.contains(" " + v) || lower.contains("=" + v)) {
				return v;
			}
		}
		return null;
	}

	private String runAgentModify(String intent, Path pomFile, Path workDir) {
		// Gather project context
		String javaVersion = JvmVersionResolver.resolve(pomFile);
		String sqlDialect = SqlDialectDetector.detect(pomFile);
		boolean isNative = NativeDetector.isNative(pomFile);

		// Try to read existing SAE analysis
		String projectAnalysis = readFileIfExists(workDir.resolve("PROJECT-ANALYSIS.md"));

		// Build agent task
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
			logger.warn("Bounded agent failed for boot-modify: {}", ex.getMessage());
			return "Agent failed: " + ex.getMessage()
					+ "\nTry a deterministic intent like: /boot-modify set java version 21";
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

				Deterministic (no AI needed):
				  /boot-modify set java version 21
				  /boot-modify clean pom

				AI-driven (interprets natural language):
				  /boot-modify add native image support
				  /boot-modify remove test dependency h2""";
	}

}
