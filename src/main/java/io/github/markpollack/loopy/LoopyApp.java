package io.github.markpollack.loopy;

import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import io.github.markpollack.loopy.command.ClearCommand;
import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.HelpCommand;
import io.github.markpollack.loopy.command.QuitCommand;
import io.github.markpollack.loopy.command.SlashCommandRegistry;
import io.github.markpollack.loopy.forge.ForgeAgentCommand;
import io.github.markpollack.loopy.tui.ChatScreen;
import io.github.markpollack.loopy.tui.LogoScreen;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "loopy", mixinStandardHelpOptions = true, version = "loopy 0.1.0-SNAPSHOT",
		description = "A loop-driven interactive coding agent CLI")
public class LoopyApp implements Callable<Integer> {

	private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

	@Option(names = { "-d", "--directory" }, description = "Working directory (default: current)")
	private Path directory;

	@Option(names = { "-m", "--model" }, description = "Model name (default: " + DEFAULT_MODEL + ")")
	private String model;

	@Option(names = { "-t", "--max-turns" }, description = "Max agent turns (default: 20)")
	private Integer maxTurns;

	@Option(names = { "-p", "--print" }, description = "Single-shot prompt (print mode)")
	private String prompt;

	@Option(names = { "--repl" }, description = "REPL mode (readline loop)")
	private boolean repl;

	@Override
	public Integer call() {
		if (this.prompt != null) {
			return runPrintMode();
		}
		if (this.repl) {
			return runReplMode();
		}
		// TUI mode (default)
		return runTui();
	}

	private int runTui() {
		String apiKey = getApiKeyOrNull();
		MiniAgent agent = null;
		ChatModel chatModel = null;

		if (apiKey != null) {
			try {
				chatModel = createChatModel(apiKey);
				agent = createAgent(chatModel, true);
			}
			catch (Exception ex) {
				System.err.println("Error creating agent: " + ex.getMessage());
				return 1;
			}
		}

		// Build slash command registry
		SlashCommandRegistry registry = createCommandRegistry(chatModel);
		Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));
		final MiniAgent finalAgent = agent;
		CommandContext ctx = new CommandContext(workDir, finalAgent != null ? finalAgent::clearSession : () -> {
		});

		java.util.function.Supplier<Model> chatScreenFactory;
		if (finalAgent != null) {
			chatScreenFactory = () -> new ChatScreen(text -> {
				var result = finalAgent.run(text);
				return result.output() != null ? result.output() : "[" + result.status() + "]";
			}, (input, ignored) -> registry.dispatch(input, ctx));
		}
		else {
			// Echo mode when no API key (for testing)
			chatScreenFactory = () -> new ChatScreen((text) -> "You said: " + text,
					(input, ignored) -> registry.dispatch(input, ctx));
		}

		LogoScreen logo = new LogoScreen(chatScreenFactory);
		Program program = new Program(logo).withAltScreen();
		program.run();
		return 0;
	}

	private Integer runPrintMode() {
		String apiKey = requireApiKey();
		if (apiKey == null) {
			return 1;
		}

		if (this.prompt.isBlank()) {
			System.err.println("Error: -p/--print requires a non-empty prompt");
			return 1;
		}

		try {
			MiniAgent agent = createAgent(createChatModel(apiKey), false);
			var result = agent.run(this.prompt);
			if (result.output() != null) {
				System.out.println(result.output());
			}
			return result.isSuccess() ? 0 : 1;
		}
		catch (Exception ex) {
			System.err.println("Error: " + ex.getMessage());
			return 1;
		}
	}

	private Integer runReplMode() {
		String apiKey = requireApiKey();
		if (apiKey == null) {
			return 1;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				PrintWriter writer = new PrintWriter(System.out, true)) {

			MiniAgent agent = null;
			ChatModel chatModel = createChatModel(apiKey);
			SlashCommandRegistry registry = createCommandRegistry(chatModel);
			Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));

			writer.println("Loopy REPL — type /help for commands, /quit to exit");
			writer.println();

			while (true) {
				writer.print("> ");
				writer.flush();

				String line = reader.readLine();
				if (line == null) {
					break;
				}

				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}

				// Slash command dispatch
				if (line.startsWith("/")) {
					CommandContext ctx = new CommandContext(workDir, agent != null ? agent::clearSession : () -> {
					});
					var cmdResult = registry.dispatch(line, ctx);
					if (cmdResult.isPresent()) {
						String result = cmdResult.get();
						if (QuitCommand.QUIT_SENTINEL.equals(result)) {
							break;
						}
						writer.println(result);
						continue;
					}
				}

				// Lazy agent creation
				if (agent == null) {
					try {
						agent = createAgent(chatModel, true);
					}
					catch (Exception ex) {
						writer.println("Error creating agent: " + ex.getMessage());
						continue;
					}
				}

				try {
					var result = agent.run(line);
					if (result.output() != null) {
						writer.println(result.output());
					}
					else {
						writer.printf("[%s]%n", result.status());
					}
				}
				catch (Exception ex) {
					writer.println("Error: " + ex.getMessage());
				}
				writer.println();
			}

			writer.println("Goodbye!");
			return 0;
		}
		catch (IOException ex) {
			System.err.println("Error: " + ex.getMessage());
			return 1;
		}
	}

	private SlashCommandRegistry createCommandRegistry(@Nullable ChatModel chatModel) {
		SlashCommandRegistry registry = new SlashCommandRegistry();
		registry.register(new HelpCommand(registry));
		registry.register(new ClearCommand());
		registry.register(new ForgeAgentCommand(chatModel));
		registry.register(new QuitCommand());
		return registry;
	}

	private ChatModel createChatModel(String apiKey) {
		var anthropicApi = AnthropicApi.builder().apiKey(apiKey).build();
		String modelName = this.model != null ? this.model : DEFAULT_MODEL;
		return AnthropicChatModel.builder()
			.anthropicApi(anthropicApi)
			.defaultOptions(AnthropicChatOptions.builder().model(modelName).maxTokens(4096).build())
			.build();
	}

	private MiniAgent createAgent(ChatModel chatModel, boolean withSession) {
		Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));
		int turns = this.maxTurns != null ? this.maxTurns : 20;

		var config = MiniAgentConfig.builder()
			.workingDirectory(workDir)
			.maxTurns(turns)
			.commandTimeout(Duration.ofSeconds(120))
			.build();

		// CLAUDE.md auto-injection — append to default system prompt
		String claudeMd = readClaudeMd(workDir);
		if (claudeMd != null) {
			String basePrompt = config.systemPrompt();
			config = config.apply(b -> b.systemPrompt(basePrompt + "\n\n## Project Instructions\n" + claudeMd));
		}

		var builder = MiniAgent.builder().config(config).model(chatModel);

		if (withSession) {
			builder.sessionMemory();
		}

		return builder.build();
	}

	private String readClaudeMd(Path workDir) {
		Path claudeMdPath = workDir.resolve("CLAUDE.md");
		if (Files.isRegularFile(claudeMdPath)) {
			try {
				return Files.readString(claudeMdPath);
			}
			catch (IOException ex) {
				// Silently ignore — not critical
			}
		}
		return null;
	}

	private String getApiKeyOrNull() {
		String key = System.getenv("ANTHROPIC_API_KEY");
		return (key != null && !key.isBlank()) ? key : null;
	}

	private String requireApiKey() {
		String key = getApiKeyOrNull();
		if (key == null) {
			System.err.println("Error: ANTHROPIC_API_KEY environment variable not set");
		}
		return key;
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new LoopyApp()).execute(args);
		System.exit(exitCode);
	}

}
