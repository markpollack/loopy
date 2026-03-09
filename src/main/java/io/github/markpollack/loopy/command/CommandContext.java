package io.github.markpollack.loopy.command;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Context passed to slash commands during execution.
 *
 * @param workingDirectory the working directory for the session
 * @param clearSession runnable that clears agent session memory
 * @param setModel consumer that switches the active model at runtime
 */
public record CommandContext(Path workingDirectory, Runnable clearSession, Consumer<String> setModel) {

	/** Convenience constructor — model switching is a no-op (for testing). */
	public CommandContext(Path workingDirectory, Runnable clearSession) {
		this(workingDirectory, clearSession, model -> {
		});
	}

}
