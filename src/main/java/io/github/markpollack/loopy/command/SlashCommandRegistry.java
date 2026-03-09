package io.github.markpollack.loopy.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps {@code /name} input to {@link SlashCommand} handlers.
 */
public class SlashCommandRegistry {

	private final Map<String, SlashCommand> commands = new LinkedHashMap<>();

	public void register(SlashCommand command) {
		this.commands.put(command.name().toLowerCase(), command);
		for (String alias : command.aliases()) {
			this.commands.put(alias.toLowerCase(), command);
		}
	}

	/**
	 * Dispatch user input to the matching command.
	 * @param input raw user input (e.g., {@code "/help"} or {@code "/forge-new --brief
	 * x"})
	 * @param context command execution context
	 * @return {@link Optional#empty()} if input doesn't start with {@code /} (caller
	 * should route to agent); otherwise the command result string
	 */
	public Optional<String> dispatch(String input, CommandContext context) {
		if (!input.startsWith("/")) {
			return Optional.empty();
		}

		String[] parts = input.substring(1).split("\\s+", 2);
		String name = parts[0].toLowerCase();
		String args = parts.length > 1 ? parts[1].trim() : "";

		SlashCommand command = this.commands.get(name);
		if (command == null) {
			return Optional.of("Unknown command: /" + name + ". Type /help for available commands.");
		}

		return Optional.of(command.execute(args, context));
	}

	/**
	 * Returns all registered commands in registration order, excluding aliases.
	 */
	public List<SlashCommand> commands() {
		return this.commands.values().stream().distinct().toList();
	}

}
