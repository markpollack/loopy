package io.github.markpollack.loopy.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandRegistryTest {

	private SlashCommandRegistry registry;

	private CommandContext context;

	@BeforeEach
	void setUp() {
		this.registry = new SlashCommandRegistry();
		this.registry.register(new HelpCommand(this.registry));
		this.registry.register(new ClearCommand());
		this.registry.register(new QuitCommand());
		this.context = new CommandContext(Path.of("/tmp"), () -> {
		});
	}

	@Test
	void nonSlashInputReturnsEmpty() {
		Optional<String> result = this.registry.dispatch("hello", this.context);
		assertThat(result).isEmpty();
	}

	@Test
	void unknownCommandReturnsError() {
		Optional<String> result = this.registry.dispatch("/foo", this.context);
		assertThat(result).isPresent();
		assertThat(result.get()).contains("Unknown command: /foo");
	}

	@Test
	void helpListsCommands() {
		Optional<String> result = this.registry.dispatch("/help", this.context);
		assertThat(result).isPresent();
		assertThat(result.get()).contains("/help");
		assertThat(result.get()).contains("/clear");
		assertThat(result.get()).contains("/exit");
	}

	@Test
	void clearInvokesRunnable() {
		AtomicBoolean cleared = new AtomicBoolean(false);
		CommandContext ctx = new CommandContext(Path.of("/tmp"), () -> cleared.set(true));
		Optional<String> result = this.registry.dispatch("/clear", ctx);
		assertThat(result).isPresent();
		assertThat(result.get()).contains("Session cleared");
		assertThat(cleared).isTrue();
	}

	@Test
	void quitReturnsSentinel() {
		Optional<String> result = this.registry.dispatch("/quit", this.context);
		assertThat(result).isPresent();
		assertThat(result.get()).isEqualTo(QuitCommand.QUIT_SENTINEL);
	}

	@Test
	void caseInsensitive() {
		Optional<String> result = this.registry.dispatch("/HELP", this.context);
		assertThat(result).isPresent();
		assertThat(result.get()).contains("/help");
	}

	@Test
	void commandWithArgs() {
		// Register a test command that echoes its args
		this.registry.register(new SlashCommand() {
			@Override
			public String name() {
				return "echo";
			}

			@Override
			public String description() {
				return "Echo args";
			}

			@Override
			public String execute(String args, CommandContext context) {
				return "Echo: " + args;
			}
		});
		Optional<String> result = this.registry.dispatch("/echo hello world", this.context);
		assertThat(result).isPresent();
		assertThat(result.get()).isEqualTo("Echo: hello world");
	}

	@Test
	void commandsListPreservesOrder() {
		assertThat(this.registry.commands()).hasSize(3);
		assertThat(this.registry.commands().get(0).name()).isEqualTo("help");
		assertThat(this.registry.commands().get(1).name()).isEqualTo("clear");
		assertThat(this.registry.commands().get(2).name()).isEqualTo("exit");
	}

}
