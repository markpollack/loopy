package io.github.markpollack.loopy.command;

/**
 * Clears the agent session memory.
 */
public class ClearCommand implements SlashCommand {

	@Override
	public String name() {
		return "clear";
	}

	@Override
	public String description() {
		return "Clear session memory";
	}

	@Override
	public String execute(String args, CommandContext context) {
		if (context.clearSession() != null) {
			context.clearSession().run();
			return "Session cleared.";
		}
		return "No session to clear.";
	}

}
