package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;

/**
 * {@code /boot-modify} slash command — delegates the user's natural language intent to
 * the agent loop, where {@link BootModifyTool} handles it via LLM tool calling.
 *
 * <p>
 * The agent selects the appropriate {@link BootModifyTool} method based on the intent and
 * calls it with the right parameters. All POM mutations execute deterministically via
 * Maven's object model — the LLM only picks the tool, it never edits XML directly.
 * </p>
 *
 * <pre>
 * /boot-modify set java version 21
 * /boot-modify add native image support
 * /boot-modify clean up the pom
 * /boot-modify add multi-arch CI
 * /boot-modify add dependency org.springframework.boot:spring-boot-starter-web
 * /boot-modify remove h2 dependency
 * </pre>
 */
public class BootModifyCommand implements SlashCommand {

	@Override
	public String name() {
		return "boot-modify";
	}

	@Override
	public String description() {
		return "Apply structural modifications to a Spring Boot project (set java version, add native support, add CI, clean pom, and more)";
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
		return context.agentDelegate()
			.apply("Modify the Spring Boot project in the working directory. The user wants to: '" + args.trim() + "'");
	}

	private static String helpText() {
		return """
				Usage: /boot-modify <intent>

				Examples:
				  /boot-modify set java version 21
				  /boot-modify add native image support
				  /boot-modify clean up the pom
				  /boot-modify add multi-arch CI
				  /boot-modify add actuator
				  /boot-modify add security
				  /boot-modify add spring format enforcement
				  /boot-modify add dependency org.springframework.boot:spring-boot-starter-web
				  /boot-modify remove h2 dependency""";
	}

}
