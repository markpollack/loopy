package io.github.markpollack.loopy.boot;

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyAliases.KeyAlias;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.HelpCommand;
import io.github.markpollack.loopy.command.QuitCommand;
import io.github.markpollack.loopy.command.SlashCommandRegistry;
import io.github.markpollack.loopy.tui.ChatEntry;
import io.github.markpollack.loopy.tui.ChatScreen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for /boot-new routing through the full TUI → registry → command
 * stack. Uses --no-llm to stay deterministic and fast.
 */
class BootNewSlashCommandIntegrationTest {

	@TempDir
	Path tempDir;

	@BeforeAll
	static void initTerminalInfo() {
		TerminalInfo.provide(() -> new TerminalInfo(false, null));
	}

	private ChatScreen screen() {
		SlashCommandRegistry registry = new SlashCommandRegistry();
		registry.register(new HelpCommand(registry));
		registry.register(new QuitCommand());
		registry.register(new BootNewCommand(null)); // null = no LLM
		CommandContext ctx = new CommandContext(tempDir, () -> {
		});
		return new ChatScreen(text -> "agent: " + text, (input, ignored) -> registry.dispatch(input, ctx));
	}

	@Test
	void bootNewScaffoldsProjectThroughTui() throws Exception {
		ChatScreen screen = screen();

		screen.setInputValue("/boot-new --name my-app --group com.example --no-llm");
		UpdateResult<? extends Model> result = pressEnter(screen);

		// Slash commands are async — spinner should be active immediately
		assertThat(screen.isWaiting()).isTrue();
		assertThat(screen.history()).isEmpty();

		// Simulate the async command completing
		Message replyMsg = executeCommand(result.command());
		screen.update(replyMsg);

		assertThat(screen.isWaiting()).isFalse();
		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(0))
			.isEqualTo(ChatEntry.user("/boot-new --name my-app --group com.example --no-llm"));
		assertThat(screen.history().get(1).content()).contains("my-app");

		// Verify the project was actually scaffolded
		assertThat(tempDir.resolve("my-app/pom.xml")).isRegularFile();
		assertThat(tempDir.resolve("my-app/src/main/java/com/example/myapp/Application.java")).isRegularFile();
	}

	@Test
	void bootNewWithoutNameReturnsUsageHelp() {
		ChatScreen screen = screen();

		screen.setInputValue("/boot-new --group com.example --no-llm");
		UpdateResult<? extends Model> result = pressEnter(screen);

		Message replyMsg = executeCommand(result.command());
		screen.update(replyMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(1).content()).containsIgnoringCase("usage");
	}

	@Test
	void bootNewWithRestTemplate() throws Exception {
		ChatScreen screen = screen();

		screen.setInputValue("/boot-new --name api-svc --group com.demo --template spring-boot-rest --no-llm");
		UpdateResult<? extends Model> result = pressEnter(screen);

		Message replyMsg = executeCommand(result.command());
		screen.update(replyMsg);

		assertThat(screen.history().get(1).content()).contains("api-svc");
		assertThat(tempDir.resolve("api-svc/src/main/java/com/demo/apisvc/greeting/GreetingController.java"))
			.isRegularFile();
	}

	@Test
	void helpCommandListsBootNew() {
		ChatScreen screen = screen();

		screen.setInputValue("/help");
		UpdateResult<? extends Model> result = pressEnter(screen);

		Message replyMsg = executeCommand(result.command());
		screen.update(replyMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(1).content()).containsIgnoringCase("boot-new");
	}

	@Test
	void unknownCommandShowsError() {
		ChatScreen screen = screen();

		screen.setInputValue("/no-such-command");
		UpdateResult<? extends Model> result = pressEnter(screen);

		Message replyMsg = executeCommand(result.command());
		screen.update(replyMsg);

		assertThat(screen.history()).hasSize(2);
		assertThat(screen.history().get(1).content()).containsIgnoringCase("unknown");
	}

	@Test
	void naturalLanguageGoesToAgent() {
		ChatScreen screen = screen();

		// NL input does not start with '/' — goes to agent, not boot-new
		screen.setInputValue("scaffold a spring boot app");
		UpdateResult<? extends Model> result = pressEnter(screen);

		// Agent call is async too — user entry added immediately
		assertThat(screen.history()).hasSize(1);
		assertThat(screen.history().get(0)).isEqualTo(ChatEntry.user("scaffold a spring boot app"));
		assertThat(screen.isWaiting()).isTrue();

		// No project scaffolded
		assertThat(tempDir.resolve("scaffold-a-spring-boot-app")).doesNotExist();
	}

	// --- Helpers ---

	private UpdateResult<? extends Model> pressEnter(ChatScreen screen) {
		var enter = new KeyPressMessage(new Key(KeyAliases.getKeyType(KeyAlias.KeyEnter)));
		return screen.update(enter);
	}

	private Message executeCommand(Command command) {
		if (command == null) {
			return null;
		}
		Message msg = command.execute();
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
