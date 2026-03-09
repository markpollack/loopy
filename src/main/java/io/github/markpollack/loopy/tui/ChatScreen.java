package io.github.markpollack.loopy.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbletea.bubbles.textinput.TextInput;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases.KeyAlias;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbles.spinner.Spinner;
import com.williamcallahan.tui4j.compat.bubbles.spinner.SpinnerType;
import com.williamcallahan.tui4j.compat.bubbles.spinner.TickMessage;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import io.github.markpollack.loopy.command.QuitCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.williamcallahan.tui4j.compat.bubbletea.Command.batch;

/**
 * Elm Architecture Model for Loopy's TUI.
 * <p>
 * Manages chat history, text input, and routes user input to either the slash command
 * registry or the agent function. Agent calls are non-blocking — they run on a background
 * thread via tui4j's Command thunk pattern.
 */
public class ChatScreen implements Model {

	private static final Style BORDER_STYLE = Style.newStyle().faint(true);

	private static final Style HINT_STYLE = Style.newStyle().foreground(Color.color("#565F89"));

	private static final Style SPINNER_STYLE = Style.newStyle().foreground(Color.color("#7AA2F7"));

	// --- Private async Message types ---

	private record AgentReplyMessage(String text) implements Message {
	}

	private record AgentErrorMessage(String text, Throwable cause) implements Message {
	}

	// --- Fields ---

	private final TextInput input;

	private final List<ChatEntry> history;

	private final Function<String, String> agentFunction;

	private final BiFunction<String, Object, Optional<String>> commandDispatcher;

	private boolean waiting;

	private Spinner spinner;

	private int termWidth = 80;

	/**
	 * Creates a ChatScreen with echo mode and no command dispatcher (for testing).
	 */
	public ChatScreen() {
		this((text) -> "You said: " + text, null);
	}

	/**
	 * Creates a ChatScreen with the given agent function and optional command dispatcher.
	 * @param agentFunction function that sends input to MiniAgent and returns response
	 * @param commandDispatcher slash command dispatcher: (input, context) →
	 * Optional(result). Null means no slash command support.
	 */
	public ChatScreen(Function<String, String> agentFunction,
			BiFunction<String, Object, Optional<String>> commandDispatcher) {
		this.history = new ArrayList<>();
		this.agentFunction = agentFunction;
		this.commandDispatcher = commandDispatcher;
		this.waiting = false;
		this.spinner = new Spinner(SpinnerType.DOT);
		this.input = new TextInput();
		this.input.setPrompt("❯ ");
		this.input.setPlaceholder("Type a message...");
		this.input.setCharLimit(4000);
		this.input.focus();
	}

	@Override
	public Command init() {
		return TextInput::blink;
	}

	@Override
	public UpdateResult<? extends Model> update(Message msg) {
		// Handle async messages first (agent replies, errors)
		UpdateResult<? extends Model> asyncResult = handleAsyncMessage(msg);
		if (asyncResult != null) {
			return asyncResult;
		}

		// Spinner animation while waiting
		if (msg instanceof TickMessage) {
			if (!waiting) {
				return UpdateResult.from(this);
			}
			UpdateResult<? extends Model> r = spinner.update(msg);
			return UpdateResult.from(this, r.command());
		}

		if (msg instanceof WindowSizeMessage w) {
			this.termWidth = Math.max(40, w.width());
			this.input.setWidth(this.termWidth - 4);
			return UpdateResult.from(this);
		}

		if (msg instanceof KeyPressMessage keyPress) {
			// Ctrl+C always quits
			if (KeyAliases.getKeyType(KeyAlias.KeyCtrlC) == keyPress.type()) {
				return new UpdateResult<>(this, QuitMessage::new);
			}

			// Enter submits the input (gated by waiting)
			if (KeyAliases.getKeyType(KeyAlias.KeyEnter) == keyPress.type()) {
				if (waiting) {
					return UpdateResult.from(this);
				}
				String text = input.value().trim();
				if (!text.isEmpty()) {
					return submitInput(text);
				}
				return UpdateResult.from(this);
			}
		}

		// Delegate other messages to TextInput
		UpdateResult<? extends Model> inputResult = input.update(msg);
		return new UpdateResult<>(this, inputResult.command());
	}

	/**
	 * Handles async agent response messages. Returns null if message is not an async
	 * type.
	 */
	private UpdateResult<? extends Model> handleAsyncMessage(Message msg) {
		if (msg instanceof AgentReplyMessage reply) {
			this.history.add(ChatEntry.assistant(reply.text()));
			this.waiting = false;
			return UpdateResult.from(this);
		}
		if (msg instanceof AgentErrorMessage error) {
			this.history.add(ChatEntry.system("Error: " + error.text()));
			this.waiting = false;
			return UpdateResult.from(this);
		}
		return null;
	}

	/**
	 * Submits user input. Slash commands are synchronous. Agent calls are async via
	 * Command thunks.
	 */
	private UpdateResult<ChatScreen> submitInput(String text) {
		// Try slash command dispatch first (synchronous)
		if (this.commandDispatcher != null) {
			Optional<String> commandResult = this.commandDispatcher.apply(text, null);
			if (commandResult.isPresent()) {
				String result = commandResult.get();
				// /exit or /quit — show goodbye then quit
				if (QuitCommand.QUIT_SENTINEL.equals(result)) {
					this.history.add(ChatEntry.user(text));
					this.history.add(ChatEntry.system("⎿  Goodbye!"));
					this.input.setValue("");
					return UpdateResult.from(this, QuitMessage::new);
				}
				this.history.add(ChatEntry.user(text));
				this.history.add(ChatEntry.system(result));
				this.input.setValue("");
				return UpdateResult.from(this);
			}
		}

		// Async agent call
		this.history.add(ChatEntry.user(text));
		this.input.setValue("");
		this.waiting = true;
		this.spinner = new Spinner(SpinnerType.DOT);

		Command agentCall = () -> {
			try {
				String response = this.agentFunction.apply(text);
				return new AgentReplyMessage(response);
			}
			catch (Exception ex) {
				return new AgentErrorMessage(ex.getMessage(), ex);
			}
		};

		return UpdateResult.from(this, batch(agentCall, spinner.init()));
	}

	@Override
	public String view() {
		StringBuilder sb = new StringBuilder();

		// Conversation history
		if (this.history.isEmpty()) {
			sb.append("\n");
		}
		else {
			for (ChatEntry entry : this.history) {
				String prefix = switch (entry.role()) {
					case USER -> "You: ";
					case ASSISTANT -> "Assistant: ";
					case SYSTEM -> "System: ";
				};
				sb.append(prefix).append(entry.content()).append("\n");
			}
		}
		sb.append("\n");

		// Separator line — shows spinner inline when thinking
		if (waiting) {
			String spinnerStr = SPINNER_STYLE.render(spinner.view());
			String thinkingStr = BORDER_STYLE.render(" Thinking... ");
			int fixedLen = spinner.view().length() + " Thinking... ".length();
			int dashLen = Math.max(0, termWidth - fixedLen);
			String dashes = BORDER_STYLE.render("─".repeat(dashLen));
			sb.append(spinnerStr).append(thinkingStr).append(dashes);
		}
		else {
			sb.append(BORDER_STYLE.render("─".repeat(termWidth)));
		}
		sb.append("\n");

		// Input
		sb.append(this.input.view()).append("\n");

		// Hint line (right-aligned, only when not waiting)
		if (!waiting) {
			String hintText = "/ commands  •  /exit to quit";
			int padding = Math.max(0, termWidth - hintText.length());
			sb.append(" ".repeat(padding)).append(HINT_STYLE.render(hintText)).append("\n");
		}

		return sb.toString();
	}

	/**
	 * Returns an unmodifiable view of the conversation history.
	 */
	public List<ChatEntry> history() {
		return Collections.unmodifiableList(this.history);
	}

	/**
	 * Returns whether the screen is waiting for an agent response.
	 */
	public boolean isWaiting() {
		return this.waiting;
	}

	/**
	 * Returns the current input value.
	 */
	public String inputValue() {
		return this.input.value();
	}

	/**
	 * Returns whether the input is empty.
	 */
	public boolean isInputEmpty() {
		return this.input.isEmpty();
	}

	/**
	 * Sets the input value directly. Package-private for testing.
	 */
	void setInputValue(String value) {
		this.input.setValue(value);
	}

}
