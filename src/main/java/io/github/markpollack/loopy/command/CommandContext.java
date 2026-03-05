package io.github.markpollack.loopy.command;

import java.nio.file.Path;

/**
 * Context passed to slash commands during execution.
 *
 * @param workingDirectory the working directory for the session
 * @param clearSession runnable that clears agent session memory
 */
public record CommandContext(Path workingDirectory, Runnable clearSession) {
}
