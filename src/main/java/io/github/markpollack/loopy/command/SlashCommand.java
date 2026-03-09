package io.github.markpollack.loopy.command;

/**
 * Interface for slash command implementations.
 * <p>
 * Commands are registered with {@link SlashCommandRegistry} and dispatched when user
 * input starts with {@code /}.
 */
public interface SlashCommand {

	/**
	 * Command name without the leading {@code /} — e.g., {@code "help"} not
	 * {@code "/help"}.
	 */
	String name();

	/**
	 * Short description for help output.
	 */
	String description();

	/**
	 * Execute the command.
	 * @param args everything after the command name (trimmed), may be empty
	 * @param context command execution context
	 * @return human-readable result string displayed to the user
	 */
	String execute(String args, CommandContext context);

	/**
	 * Alternative names for this command (e.g., {@code "exit"} for {@code "quit"}).
	 */
	default java.util.List<String> aliases() {
		return java.util.List.of();
	}

	/**
	 * How command output is added to the conversation context.
	 */
	default ContextType contextType() {
		return ContextType.NONE;
	}

	/**
	 * Whether selecting from a palette should fill the composer instead of executing
	 * immediately.
	 */
	default boolean requiresArguments() {
		return false;
	}

	enum ContextType {

		/** Display only — not added to LLM context. */
		NONE,
		/** Added as SYSTEM message to LLM context. */
		SYSTEM,
		/** Added as ASSISTANT message to LLM context. */
		ASSISTANT

	}

}
