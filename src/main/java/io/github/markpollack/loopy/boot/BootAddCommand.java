package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /boot-add} slash command — bootstraps domain capabilities into an existing
 * Spring Boot project.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Resolve starter coordinates (from catalog or explicit {@code --coords})</li>
 * <li>Run SAE: {@link BootProjectAnalyzer#analyze(Path)} →
 * {@code PROJECT-ANALYSIS.md}</li>
 * <li>Add {@code <dependency>} to {@code pom.xml} via {@code MavenXpp3Reader/Writer}</li>
 * <li>Optional bounded MiniAgent (skipped with {@code --no-agent}): reads
 * {@code PROJECT-ANALYSIS.md}, generates domain-specific code for this project</li>
 * </ol>
 * </p>
 *
 * <pre>
 * Usage: /boot-add &lt;starter-name&gt; [--no-agent] [--coords groupId:artifactId[:version]]
 *
 *   &lt;starter-name&gt;  Agent Starter name (from /starters catalog, or any artifactId)
 *   --no-agent      Skip the bounded LLM code-generation step
 *   --coords        Explicit Maven coordinates: groupId:artifactId[:version]
 *                   Required when the starter name is not in the catalog.
 * </pre>
 */
public class BootAddCommand implements SlashCommand {

	private static final Logger logger = LoggerFactory.getLogger(BootAddCommand.class);

	private final @Nullable ChatModel chatModel;

	public BootAddCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "boot-add";
	}

	@Override
	public String description() {
		return "Bootstrap domain capabilities (Agent Starter) into an existing project";
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

		// Parse: first non-flag token is the starter name
		Map<String, String> flags = parseFlags(args);
		String starterName = parseStarterName(args);
		if (starterName == null) {
			return helpText();
		}

		boolean noAgent = flags.containsKey("no-agent");
		String explicitCoords = flags.get("coords");

		// Resolve Maven coordinates
		String[] coords = resolveCoordinates(starterName, explicitCoords);
		if (coords == null) {
			return "Starter '" + starterName + "' not found in catalog.\n"
					+ "Specify coordinates explicitly: /boot-add " + starterName
					+ " --coords groupId:artifactId[:version]\n"
					+ "Or use /starters search to find available starters.";
		}

		String groupId = coords[0];
		String artifactId = coords[1];
		String version = coords.length > 2 ? coords[2] : null;

		Path workDir = context.workingDirectory();
		Path pomFile = workDir.resolve("pom.xml");
		if (!Files.isRegularFile(pomFile)) {
			return "No pom.xml found in " + workDir + ". Run this command from a Maven project root.";
		}

		// Step 1: Run SAE analysis
		logger.info("Analyzing project structure...");
		BootProjectAnalyzer.analyze(workDir);

		// Step 2: Add dependency to pom.xml
		try {
			addDependency(pomFile, groupId, artifactId, version);
		}
		catch (Exception ex) {
			logger.error("Failed to modify pom.xml: {}", ex.getMessage(), ex);
			return "Error modifying pom.xml: " + ex.getMessage();
		}

		StringBuilder result = new StringBuilder();
		result.append("Added ").append(groupId).append(":").append(artifactId);
		if (version != null) {
			result.append(":").append(version);
		}
		result.append(" to pom.xml\n");
		result.append("Generated PROJECT-ANALYSIS.md in ").append(workDir).append("\n");

		// Step 3: Optional bounded agent code generation
		if (noAgent || chatModel == null) {
			result.append("\nSkipped code generation (--no-agent).");
			result.append("\nRun /boot-add " + starterName + " (without --no-agent) to generate domain-specific code.");
			return result.toString();
		}

		// Read PROJECT-ANALYSIS.md for agent context
		Path analysisFile = workDir.resolve("PROJECT-ANALYSIS.md");
		String projectAnalysis;
		try {
			projectAnalysis = Files.readString(analysisFile);
		}
		catch (IOException ex) {
			projectAnalysis = "(PROJECT-ANALYSIS.md not available)";
		}

		// Build bounded agent task
		String task = buildAgentTask(starterName, groupId, artifactId, projectAnalysis);

		try {
			MiniAgent agent = MiniAgent.builder()
				.config(io.github.markpollack.loopy.agent.MiniAgentConfig.builder()
					.workingDirectory(workDir)
					.maxTurns(10)
					.build())
				.model(chatModel)
				.build();
			var agentResult = agent.run(task);
			if (agentResult.output() != null) {
				result.append("\n").append(agentResult.output());
			}
		}
		catch (Exception ex) {
			logger.warn("Bounded agent failed: {}", ex.getMessage());
			result.append("\nCode generation failed: ").append(ex.getMessage());
			result.append("\nDependency was added — run manually to generate domain-specific code.");
		}

		return result.toString();
	}

	/**
	 * Add a dependency to pom.xml using MavenXpp3Reader/Writer (Maven object model).
	 */
	static void addDependency(Path pomFile, String groupId, String artifactId, @Nullable String version)
			throws IOException, XmlPullParserException {
		MavenXpp3Reader reader = new MavenXpp3Reader();
		Model model;
		try (FileReader fr = new FileReader(pomFile.toFile())) {
			model = reader.read(fr);
		}

		// Check if dependency already present
		boolean alreadyPresent = model.getDependencies()
			.stream()
			.anyMatch(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()));
		if (alreadyPresent) {
			logger.info("Dependency {}:{} already present — skipping", groupId, artifactId);
			return;
		}

		Dependency dep = new Dependency();
		dep.setGroupId(groupId);
		dep.setArtifactId(artifactId);
		if (version != null) {
			dep.setVersion(version);
		}
		model.addDependency(dep);

		MavenXpp3Writer writer = new MavenXpp3Writer();
		try (FileWriter fw = new FileWriter(pomFile.toFile())) {
			writer.write(fw, model);
		}
	}

	/**
	 * Resolve Maven coordinates for the starter.
	 * <ol>
	 * <li>Use explicit {@code --coords} if provided</li>
	 * <li>Look up in StartersCatalog by name</li>
	 * <li>If not found, return null (caller shows error)</li>
	 * </ol>
	 */
	private static String @Nullable [] resolveCoordinates(String starterName, @Nullable String explicitCoords) {
		if (explicitCoords != null) {
			return explicitCoords.split(":");
		}
		var catalog = StartersCatalog.load();
		var entry = catalog.findByName(starterName);
		if (entry.isPresent() && entry.get().mavenCoordinates != null) {
			return entry.get().mavenCoordinates.split(":");
		}
		return null;
	}

	/**
	 * Extract the first non-flag token as the starter name.
	 */
	static @Nullable String parseStarterName(@Nullable String args) {
		if (args == null || args.isBlank()) {
			return null;
		}
		String[] tokens = args.trim().split("\\s+");
		for (String token : tokens) {
			if (!token.startsWith("--")) {
				return token;
			}
		}
		return null;
	}

	/**
	 * Parse {@code --key value} and boolean {@code --flag} tokens.
	 */
	static Map<String, String> parseFlags(String args) {
		Map<String, String> result = new LinkedHashMap<>();
		if (args == null || args.isBlank()) {
			return result;
		}
		String[] tokens = args.trim().split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i].startsWith("--")) {
				String key = tokens[i].substring(2);
				if (i + 1 < tokens.length && !tokens[i + 1].startsWith("--")) {
					result.put(key, tokens[i + 1]);
					i++;
				}
				else {
					result.put(key, "true");
				}
			}
		}
		return result;
	}

	private static String buildAgentTask(String starterName, String groupId, String artifactId,
			String projectAnalysis) {
		return """
				You are a domain expert adding %s capabilities to this project.
				The dependency %s:%s has been added to pom.xml.

				Here is the project structure:
				%s

				Generate domain-specific code tailored to the actual classes and packages shown above.
				Focus on practical, immediately useful code patterns — not generic samples.
				""".formatted(starterName, groupId, artifactId, projectAnalysis);
	}

	private static String helpText() {
		return """
				Usage: /boot-add <starter-name> [--no-agent] [--coords groupId:artifactId[:version]]

				  <starter-name>  Agent Starter name (from /starters catalog)
				  --no-agent      Skip LLM code generation — add dependency only
				  --coords        Explicit Maven coordinates (when not in catalog)

				Example: /boot-add spring-ai-starter-data-jpa
				Example: /boot-add my-starter --coords com.example:my-starter:1.0.0""";
	}

}
