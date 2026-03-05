package io.github.markpollack.loopy.tui;

/**
 * Represents a single entry in the chat history.
 *
 * @param role the role (USER, ASSISTANT, or SYSTEM)
 * @param content the message content
 */
public record ChatEntry(Role role, String content) {

	/**
	 * Role of the chat participant.
	 */
	public enum Role {

		USER, ASSISTANT, SYSTEM

	}

	/**
	 * Creates a user message.
	 */
	public static ChatEntry user(String content) {
		return new ChatEntry(Role.USER, content);
	}

	/**
	 * Creates an assistant message.
	 */
	public static ChatEntry assistant(String content) {
		return new ChatEntry(Role.ASSISTANT, content);
	}

	/**
	 * Creates a system message (slash command output).
	 */
	public static ChatEntry system(String content) {
		return new ChatEntry(Role.SYSTEM, content);
	}

}
