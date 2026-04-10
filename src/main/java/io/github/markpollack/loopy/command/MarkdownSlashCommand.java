package io.github.markpollack.loopy.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A slash command loaded from a markdown file (Claude Code {@code ~/.claude/commands/}
 * convention).
 * <p>
 * Parses YAML front matter for {@code name} and {@code description}. On execution, reads
 * the file, substitutes {@code $ARGUMENTS} with user-provided args, and delegates the
 * expanded prompt to the agent via {@link CommandContext#agentDelegate()}.
 */
public class MarkdownSlashCommand implements SlashCommand {

	private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

	private static final Pattern FIELD = Pattern.compile("^(\\w+):\\s*\"?([^\"]*)\"?\\s*$", Pattern.MULTILINE);

	private final String name;

	private final String description;

	private final Path file;

	private MarkdownSlashCommand(String name, String description, Path file) {
		this.name = name;
		this.description = description;
		this.file = file;
	}

	/**
	 * Parse a markdown command file. Returns null if the file cannot be read or has no
	 * usable name.
	 */
	public static @Nullable MarkdownSlashCommand fromFile(Path file) {
		String content;
		try {
			content = Files.readString(file);
		}
		catch (IOException ex) {
			return null;
		}

		String name = null;
		String description = "";

		Matcher fm = FRONT_MATTER.matcher(content);
		if (fm.find()) {
			String yaml = fm.group(1);
			Matcher field = FIELD.matcher(yaml);
			while (field.find()) {
				switch (field.group(1)) {
					case "name" -> name = field.group(2).trim();
					case "description" -> description = field.group(2).trim();
				}
			}
		}

		// Fall back to filename if no front matter name
		if (name == null || name.isBlank()) {
			String filename = file.getFileName().toString();
			if (filename.endsWith(".md")) {
				name = filename.substring(0, filename.length() - 3);
			}
			else {
				return null;
			}
		}

		return new MarkdownSlashCommand(name, description, file);
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public String description() {
		return this.description;
	}

	@Override
	public boolean requiresArguments() {
		return false;
	}

	@Override
	public String execute(String args, CommandContext context) {
		String content;
		try {
			content = Files.readString(this.file);
		}
		catch (IOException ex) {
			return "Error reading command file: " + ex.getMessage();
		}

		// Substitute $ARGUMENTS placeholder
		String expanded = content.replace("$ARGUMENTS", args != null ? args : "");

		return context.agentDelegate().apply(expanded);
	}

	@Override
	public ContextType contextType() {
		return ContextType.ASSISTANT;
	}

}
