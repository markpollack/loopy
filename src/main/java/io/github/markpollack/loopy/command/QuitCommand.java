package io.github.markpollack.loopy.command;

/**
 * Exits Loopy. In TUI mode the result triggers a quit via ChatScreen; in REPL mode the
 * loop checks for this sentinel value.
 */
public class QuitCommand implements SlashCommand {

	/** Sentinel value returned by execute() that callers check to trigger exit. */
	public static final String QUIT_SENTINEL = "__QUIT__";

	@Override
	public String name() {
		return "quit";
	}

	@Override
	public String description() {
		return "Exit Loopy";
	}

	@Override
	public String execute(String args, CommandContext context) {
		return QUIT_SENTINEL;
	}

}
