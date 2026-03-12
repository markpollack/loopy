package io.github.markpollack.loopy.command;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Context passed to slash commands during execution.
 *
 * @param workingDirectory the working directory for the session
 * @param clearSession runnable that clears agent session memory
 * @param setModel consumer that switches the active model at runtime
 * @param agentDelegate function that runs a prompt through the agent loop and returns the
 * result — allows slash commands to delegate NL → tool-call execution without importing
 * the agent layer
 */
public record CommandContext(Path workingDirectory, Runnable clearSession, Consumer<String> setModel,
		Function<String, String> agentDelegate) {

	/**
	 * Convenience constructor — model switching and agent delegation are no-ops (for
	 * testing).
	 */
	public CommandContext(Path workingDirectory, Runnable clearSession) {
		this(workingDirectory, clearSession, model -> {
		}, prompt -> "Agent not available");
	}

}
