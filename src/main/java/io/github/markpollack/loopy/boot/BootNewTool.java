package io.github.markpollack.loopy.boot;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Agent tool for scaffolding new Spring Boot projects from bundled templates.
 *
 * <p>
 * Registered with MiniAgent so the agent loop can invoke it directly from natural
 * language — no slash command syntax required.
 * </p>
 *
 * <pre>
 * User: "create a REST API project called orders-api for com.acme"
 * Agent: calls bootNew("orders-api", "com.acme", "spring-boot-rest", null)
 * </pre>
 */
public class BootNewTool {

	private static final Logger logger = LoggerFactory.getLogger(BootNewTool.class);

	private static final List<String> VALID_TEMPLATES = List.of("spring-boot-minimal", "spring-boot-rest",
			"spring-boot-jpa", "spring-ai-app");

	private final Path workingDirectory;

	private final @Nullable ChatModel chatModel;

	public BootNewTool(Path workingDirectory, @Nullable ChatModel chatModel) {
		this.workingDirectory = workingDirectory;
		this.chatModel = chatModel;
	}

	@Tool(description = """
			Scaffold a new Spring Boot project from a bundled template in the working directory.
			Use when the user asks to create, generate, or scaffold a new Spring Boot project.
			Templates: spring-boot-minimal (default), spring-boot-rest, spring-boot-jpa, spring-ai-app.
			The project is created as a subdirectory named after the project name.
			""")
	public String bootNew(@ToolParam(
			description = "Project name — used as directory name and Maven artifactId (e.g. orders-api)") String name,
			@ToolParam(description = "Maven groupId (e.g. com.acme). Falls back to saved preferences if omitted.",
					required = false) @Nullable String group,
			@ToolParam(
					description = "Template: spring-boot-minimal, spring-boot-rest, spring-boot-jpa, spring-ai-app. Default: spring-boot-minimal.",
					required = false) @Nullable String template,
			@ToolParam(description = "Java major version (e.g. 21). Default: 21.",
					required = false) @Nullable String javaVersion) {

		if (name == null || name.isBlank()) {
			return "Error: project name is required.";
		}

		String resolvedTemplate = template != null ? template : "spring-boot-minimal";
		if (!VALID_TEMPLATES.contains(resolvedTemplate)) {
			return "Unknown template '" + resolvedTemplate + "'. Available: " + String.join(", ", VALID_TEMPLATES);
		}

		String resolvedGroup = group;
		if (resolvedGroup == null) {
			resolvedGroup = BootPreferences.load().groupId();
		}
		if (resolvedGroup == null) {
			return "No groupId provided and none saved in preferences. "
					+ "Provide a group (e.g. com.acme) or run /boot-setup to save defaults.";
		}

		String artifactId = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
		BootBrief brief = new BootBrief(name, resolvedGroup, artifactId, null, resolvedTemplate, javaVersion);

		try {
			logger.debug("Scaffolding '{}' from template '{}' in {}", name, resolvedTemplate, workingDirectory);
			return ScaffoldGraph.execute(brief, false, workingDirectory, chatModel);
		}
		catch (Exception ex) {
			logger.error("Scaffolding failed for '{}': {}", name, ex.getMessage(), ex);
			return "Error scaffolding project: " + ex.getMessage();
		}
	}

}
