package io.github.markpollack.loopy.command;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * {@code /btw} — ask a quick side question without touching session memory.
 *
 * <p>
 * Makes a single stateless LLM call. The question and answer are shown in the TUI but
 * never added to MiniAgent's conversation context, so ongoing work is not interrupted.
 * </p>
 *
 * <pre>
 * /btw what does @Transactional(readOnly=true) actually do?
 * </pre>
 */
public class BtwCommand implements SlashCommand {

	private final @Nullable ChatModel chatModel;

	public BtwCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "btw";
	}

	@Override
	public String description() {
		return "Ask a quick side question without interrupting session context";
	}

	@Override
	public boolean requiresArguments() {
		return true;
	}

	@Override
	public String execute(String args, CommandContext context) {
		if (args == null || args.isBlank()) {
			return "Usage: /btw <question>\nExample: /btw what does @Transactional(readOnly=true) do?";
		}

		if (chatModel == null) {
			return "Error: no LLM available (ANTHROPIC_API_KEY not set?)";
		}

		try {
			var response = chatModel.call(new Prompt(args));
			return response.getResult().getOutput().getText();
		}
		catch (Exception ex) {
			return "Error: " + ex.getMessage();
		}
	}

}
