package io.github.markpollack.loopy.command;

import java.util.List;

/**
 * Lists all registered slash commands with descriptions.
 */
public class HelpCommand implements SlashCommand {

	private final SlashCommandRegistry registry;

	public HelpCommand(SlashCommandRegistry registry) {
		this.registry = registry;
	}

	@Override
	public String name() {
		return "help";
	}

	@Override
	public String description() {
		return "List available commands";
	}

	@Override
	public String execute(String args, CommandContext context) {
		List<SlashCommand> commands = this.registry.commands();
		StringBuilder sb = new StringBuilder("Available commands:\n");
		for (SlashCommand cmd : commands) {
			sb.append("  /").append(cmd.name()).append(" — ").append(cmd.description()).append("\n");
		}
		return sb.toString().stripTrailing();
	}

}
