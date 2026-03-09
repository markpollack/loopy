package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /boot-new} slash command — scaffolds a new Spring Boot project from a bundled
 * stem cell template.
 *
 * <pre>
 * Usage: /boot-new --name &lt;name&gt; [--group &lt;groupId&gt;] [--template &lt;template&gt;] [--no-llm]
 *
 * Options:
 *   --name       Project name (required). Used as directory name and artifactId.
 *   --group      Maven groupId (required on first use; saved to prefs after).
 *   --template   Template: spring-boot-minimal (default), spring-boot-rest,
 *                          spring-boot-jpa, spring-ai-app
 *   --no-llm     Skip the LLM customisation step (deterministic-only mode).
 * </pre>
 */
public class BootNewCommand implements SlashCommand {

	private static final Logger logger = LoggerFactory.getLogger(BootNewCommand.class);

	private static final List<String> VALID_TEMPLATES = List.of("spring-boot-minimal", "spring-boot-rest",
			"spring-boot-jpa", "spring-ai-app");

	private final @Nullable ChatModel chatModel;

	public BootNewCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "boot-new";
	}

	@Override
	public String description() {
		return "Scaffold a new Spring Boot project from a bundled template";
	}

	@Override
	public boolean requiresArguments() {
		return true;
	}

	@Override
	public String execute(String args, CommandContext context) {
		Map<String, String> flags = parseFlags(args);

		// Validate --name (required)
		String name = flags.get("name");
		if (name == null || name.isBlank()) {
			return helpText();
		}

		String template = flags.getOrDefault("template", "spring-boot-minimal");
		if (!VALID_TEMPLATES.contains(template)) {
			return "Unknown template '" + template + "'. Available: " + String.join(", ", VALID_TEMPLATES);
		}

		String groupId = flags.get("group");
		boolean noLlm = flags.containsKey("no-llm");

		// groupId required: from arg or saved prefs
		BootPreferences prefs = BootPreferences.load();
		if (groupId == null) {
			groupId = prefs.groupId();
		}
		if (groupId == null) {
			return "Error: --group is required on first use.\n" + "Example: /boot-new --name my-app --group com.acme\n"
					+ "After the first run it is saved to preferences automatically.";
		}

		String artifactId = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
		BootBrief brief = new BootBrief(name, groupId, artifactId, null, template, null);

		try {
			String result = ScaffoldGraph.execute(brief, noLlm, context.workingDirectory(), chatModel);
			savePreferences(prefs, groupId);
			return result;
		}
		catch (Exception ex) {
			logger.error("Scaffolding failed for '{}': {}", name, ex.getMessage(), ex);
			return "Error: " + ex.getMessage();
		}
	}

	private void savePreferences(BootPreferences existing, String effectiveGroupId) {
		String jv = existing.javaVersion() != null ? existing.javaVersion() : "21";
		new BootPreferences(jv, effectiveGroupId, existing.alwaysAdd(), existing.preferDatabase()).save();
	}

	/**
	 * Parse {@code --key value} and boolean {@code --flag} tokens from a string.
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

	private static String helpText() {
		return """
				Usage: /boot-new --name <name> [--group <groupId>] [--template <template>] [--no-llm]

				  --name      Project name (required). Also used as directory name and artifactId.
				  --group     Maven groupId (required on first use; saved to preferences).
				  --template  One of: spring-boot-minimal (default), spring-boot-rest,
				              spring-boot-jpa, spring-ai-app
				  --no-llm    Skip LLM customisation — deterministic only.

				Example: /boot-new --name products-api --group com.acme --template spring-boot-rest""";
	}

}
