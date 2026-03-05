package io.github.markpollack.loopy.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases.KeyAlias;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.markpollack.loopy.command.QuitCommand;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatScreenTest {

	@BeforeAll
	static void initTerminalInfo() {
		TerminalInfo.provide(() -> new TerminalInfo(false, null));
	}

	@Test
	void enterSubmitsInputAndReturnsCommand() {
		var screen = new ChatScreen((text) -> "echo: " + text, null);

		screen.setInputValue("hello");
		UpdateResult<? extends Model> result = pressEnter(screen);

		// User entry added immediately, agent reply comes async
		assertThat(screen.history()).hasSize(1);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("hello"));
		assertThat(screen.isWaiting()).isTrue();
		// Command thunk should be returned for async execution
		assertThat(result.command()).isNotNull();
	}

	@Test
	void agentReplyMessageAddsAssistantEntry() {
		var screen = new ChatScreen((text) -> "echo: " + text, null);

		screen.setInputValue("hello");
		UpdateResult<? extends Model> submitResult = pressEnter(screen);

		// Simulate what tui4j does: execute the Command thunk and deliver the Message
		Message replyMsg = executeCommand(submitResult.command());
		screen.update(replyMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("hello"));
		assertThat(screen.history().get(1)).isEqualTo(ChatEntry.assistant("echo: hello"));
		assertThat(screen.isWaiting()).isFalse();
	}

	@Test
	void agentErrorAddsSystemEntry() {
		var screen = new ChatScreen((text) -> {
			throw new RuntimeException("API down");
		}, null);

		screen.setInputValue("hello");
		UpdateResult<? extends Model> submitResult = pressEnter(screen);

		Message errorMsg = executeCommand(submitResult.command());
		screen.update(errorMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("hello"));
		assertThat(screen.history().get(1)).isEqualTo(ChatEntry.system("Error: API down"));
		assertThat(screen.isWaiting()).isFalse();
	}

	@Test
	void enterIgnoredWhileWaiting() {
		var screen = new ChatScreen((text) -> "echo: " + text, null);

		screen.setInputValue("first");
		pressEnter(screen);
		assertThat(screen.isWaiting()).isTrue();

		// Try to submit again while waiting — should be ignored
		screen.setInputValue("second");
		pressEnter(screen);

		// Only the first user entry should be in history
		assertThat(screen.history()).hasSize(1);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("first"));
	}

	@Test
	void slashCommandIntercepted() {
		var screen = new ChatScreen((text) -> "agent: " + text,
				(input, ctx) -> input.startsWith("/") ? Optional.of("command result") : Optional.empty());

		screen.setInputValue("/help");
		pressEnter(screen);

		// Slash commands are synchronous — both entries added immediately
		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("/help"));
		assertThat(screen.history().get(1)).isEqualTo(ChatEntry.system("command result"));
		assertThat(screen.isWaiting()).isFalse();
	}

	@Test
	void nonSlashPassesToAgent() {
		var screen = new ChatScreen((text) -> "agent: " + text,
				(input, ctx) -> input.startsWith("/") ? Optional.of("cmd") : Optional.empty());

		screen.setInputValue("hello");
		UpdateResult<? extends Model> submitResult = pressEnter(screen);

		Message replyMsg = executeCommand(submitResult.command());
		screen.update(replyMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(1)).isEqualTo(ChatEntry.assistant("agent: hello"));
	}

	@Test
	void quitCommandTriggersExit() {
		var screen = new ChatScreen((text) -> "agent: " + text,
				(input, ctx) -> "/quit".equals(input) ? Optional.of(QuitCommand.QUIT_SENTINEL) : Optional.empty());

		screen.setInputValue("/quit");
		UpdateResult<? extends Model> result = pressEnter(screen);

		// Quit sentinel should produce a quit command, not add to history
		assertThat(result.command()).isNotNull();
		assertThat(screen.history()).isEmpty();
	}

	@Test
	void ctrlCQuits() {
		var screen = new ChatScreen();
		var keyPress = new KeyPressMessage(new Key(KeyAliases.getKeyType(KeyAlias.KeyCtrlC)));
		UpdateResult<? extends Model> result = screen.update(keyPress);
		assertThat(result.command()).isNotNull();
	}

	@Test
	void viewShowsSpinnerWhileWaiting() {
		var screen = new ChatScreen((text) -> "echo: " + text, null);
		screen.setInputValue("hello");
		pressEnter(screen);

		assertThat(screen.isWaiting()).isTrue();
		assertThat(screen.view()).contains("Thinking...");
	}

	@Test
	void inputClearedAfterSubmit() {
		var screen = new ChatScreen((text) -> "echo: " + text, null);
		screen.setInputValue("hello");
		pressEnter(screen);

		assertThat(screen.inputValue()).isEmpty();
	}

	// --- Helpers ---

	private UpdateResult<? extends Model> pressEnter(ChatScreen screen) {
		var enter = new KeyPressMessage(new Key(KeyAliases.getKeyType(KeyAlias.KeyEnter)));
		return screen.update(enter);
	}

	/**
	 * Executes a Command thunk and returns the first concrete Message (unwrapping batch).
	 * In tui4j, batch() wraps commands in a BatchMessage — we drill through to find the
	 * agent reply.
	 */
	private Message executeCommand(Command command) {
		if (command == null) {
			return null;
		}
		Message msg = command.execute();
		// BatchMessage wraps multiple commands — execute each and return the first
		// non-null, non-batch result
		if (msg instanceof com.williamcallahan.tui4j.compat.bubbletea.BatchMessage batchMsg) {
			for (Command cmd : batchMsg.commands()) {
				Message inner = executeCommand(cmd);
				if (inner != null && !(inner instanceof com.williamcallahan.tui4j.compat.bubbletea.BatchMessage)
						&& !(inner instanceof com.williamcallahan.tui4j.compat.bubbles.spinner.TickMessage)) {
					return inner;
				}
			}
		}
		return msg;
	}

}
