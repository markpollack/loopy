package io.github.markpollack.loopy.boot;

import io.github.markpollack.harness.patterns.graph.GraphCompositionStrategy;
import io.github.markpollack.harness.patterns.graph.GraphContext;
import io.github.markpollack.harness.patterns.graph.GraphResult;
import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Builds and executes the five-node scaffolding workflow graph for {@code /boot-new}.
 *
 * <pre>
 * apply-preferences → extract-from-jar → deterministic-customize
 *     → [llm-domain-fill →] finish
 * </pre>
 *
 * <p>
 * The LLM node is skipped when {@code --no-llm} is given or no {@link ChatModel} is
 * available.
 * </p>
 */
public final class ScaffoldGraph {

	private static final Logger logger = LoggerFactory.getLogger(ScaffoldGraph.class);

	private ScaffoldGraph() {
	}

	/**
	 * Execute the scaffolding graph.
	 * @param brief initial brief (groupId and packageName may be null — filled by prefs)
	 * @param noLlm skip LLM customisation node
	 * @param workDir parent directory — project will be created at
	 * {@code workDir/brief.name()}
	 * @param chatModel model for the LLM node; ignored when {@code noLlm} is true
	 * @return human-readable success message
	 * @throws RuntimeException if the graph fails
	 */
	public static String execute(BootBrief brief, boolean noLlm, Path workDir, @Nullable ChatModel chatModel) {
		Path targetDir = workDir.resolve(brief.name());
		boolean useLlm = !noLlm && chatModel != null;

		var b = GraphCompositionStrategy.<BootBrief, String>builder("boot-new");

		// Declare nodes BEFORE startNode/finishNode (builder ordering rule)
		b.node("apply-preferences", (GraphContext ctx, BootBrief br) -> applyPreferences(br));
		b.node("extract-from-jar", (GraphContext ctx, BootBrief br) -> extractTemplate(br, targetDir));
		b.node("deterministic-customize", (GraphContext ctx, BootBrief br) -> customizeTemplate(br, targetDir));
		b.node("finish", (GraphContext ctx, BootBrief br) -> buildResultMessage(br, targetDir));
		if (useLlm) {
			final ChatModel model = chatModel;
			b.node("llm-domain-fill", (GraphContext ctx, BootBrief br) -> llmDomainFill(br, targetDir, model));
		}

		b.startNode("apply-preferences");
		b.finishNode("finish");

		b.edge("apply-preferences").to("extract-from-jar").and();
		b.edge("extract-from-jar").to("deterministic-customize").and();
		b.edge("deterministic-customize").to(useLlm ? "llm-domain-fill" : "finish").and();
		if (useLlm) {
			b.edge("llm-domain-fill").to("finish").and();
		}

		GraphCompositionStrategy<BootBrief, String> graph = b.build();
		GraphResult<String> result = graph.execute(brief);

		if (result.isSuccess()) {
			return result.output();
		}
		String detail = result.error() != null ? result.error().getMessage() : result.status().name();
		throw new RuntimeException("Scaffolding failed [" + result.status() + "]: " + detail);
	}

	// ── Node implementations ─────────────────────────────────────────────────────

	private static BootBrief applyPreferences(BootBrief brief) {
		BootBrief filled = BootPreferences.load().applyToBootBrief(brief);
		// Derive packageName if not explicitly set
		if (filled.packageName() == null && filled.groupId() != null) {
			String sanitized = filled.artifactId().toLowerCase().replaceAll("[^a-z0-9]", "");
			filled = new BootBrief(filled.name(), filled.groupId(), filled.artifactId(),
					filled.groupId() + "." + sanitized, filled.template(), filled.javaVersion());
		}
		logger.debug("Brief after preferences: {}", filled);
		return filled;
	}

	private static BootBrief extractTemplate(BootBrief brief, Path targetDir) {
		new TemplateExtractor().extract(brief.template(), targetDir);
		logger.debug("Template '{}' extracted to {}", brief.template(), targetDir);
		return brief;
	}

	private static BootBrief customizeTemplate(BootBrief brief, Path targetDir) {
		renamePomGav(brief, targetDir);
		if (brief.packageName() != null) {
			new JavaParserRefactor().refactorPackage(targetDir, BootBrief.TEMPLATE_PACKAGE, brief.packageName());
		}
		return brief;
	}

	private static BootBrief llmDomainFill(BootBrief brief, Path targetDir, ChatModel chatModel) {
		var config = MiniAgentConfig.builder()
			.workingDirectory(targetDir)
			.maxTurns(3)
			.commandTimeout(Duration.ofSeconds(60))
			.build();
		MiniAgent agent = MiniAgent.builder().config(config).model(chatModel).build();

		String task = """
				You are customizing a newly scaffolded Spring Boot project at: %s

				Template: %s
				Project name: %s
				Package: %s

				Tasks:
				1. Update README.md — describe this project based on its name and template type.
				2. If application.yml exists, add 2-3 domain-relevant configuration stubs with comments.

				Rules:
				- Do NOT modify any .java files.
				- Keep changes minimal and focused.
				- Use absolute paths for all file operations.
				""".formatted(targetDir.toAbsolutePath(), brief.template(), brief.name(),
				brief.packageName() != null ? brief.packageName() : "(not set)");

		agent.run(task);
		logger.debug("LLM domain fill completed for '{}'", brief.name());
		return brief;
	}

	private static String buildResultMessage(BootBrief brief, Path targetDir) {
		return "Created %s/ (%s template, %s package)".formatted(brief.name(), brief.template(),
				brief.packageName() != null ? brief.packageName() : brief.groupId());
	}

	// ── Helpers ──────────────────────────────────────────────────────────────────

	private static void renamePomGav(BootBrief brief, Path targetDir) {
		Path pomPath = targetDir.resolve("pom.xml");
		if (!Files.exists(pomPath)) {
			return;
		}
		try {
			String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
			if (brief.groupId() != null) {
				pom = pom.replace("<groupId>" + BootBrief.TEMPLATE_GROUP_ID + "</groupId>",
						"<groupId>" + brief.groupId() + "</groupId>");
			}
			pom = pom.replace("<artifactId>" + BootBrief.TEMPLATE_ARTIFACT_ID + "</artifactId>",
					"<artifactId>" + brief.artifactId() + "</artifactId>");
			pom = pom.replace("<name>" + BootBrief.TEMPLATE_ARTIFACT_ID + "</name>",
					"<name>" + brief.name() + "</name>");
			Files.writeString(pomPath, pom, StandardCharsets.UTF_8);
			logger.debug("pom.xml GAV updated: {}:{}", brief.groupId(), brief.artifactId());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to update pom.xml at " + pomPath, ex);
		}
	}

}
