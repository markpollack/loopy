package io.github.markpollack.loopy;

import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import com.williamcallahan.tui4j.term.TerminalInfo;

import io.github.markpollack.journal.Journal;
import io.github.markpollack.journal.Run;
import io.github.markpollack.journal.storage.JsonFileStorage;
import io.github.markpollack.loopy.agent.ConsoleToolCallListener;
import io.github.markpollack.loopy.agent.DebugLoopListener;
import io.github.markpollack.loopy.agent.DebugToolCallListener;
import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import io.github.markpollack.loopy.boot.BootAddCommand;
import io.github.markpollack.loopy.boot.BootModifyCommand;
import io.github.markpollack.loopy.boot.BootNewCommand;
import io.github.markpollack.loopy.boot.BootModifyTool;
import io.github.markpollack.loopy.boot.BootNewTool;
import io.github.markpollack.loopy.boot.BootSetupCommand;
import io.github.markpollack.loopy.boot.StartersCommand;
import io.github.markpollack.loopy.command.BtwCommand;
import io.github.markpollack.loopy.command.ClearCommand;
import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.HelpCommand;
import io.github.markpollack.loopy.command.ModelCommand;
import io.github.markpollack.loopy.command.QuitCommand;
import io.github.markpollack.loopy.command.SkillsCommand;
import io.github.markpollack.loopy.command.SlashCommandRegistry;
import io.github.markpollack.loopy.forge.ForgeAgentCommand;
import io.github.markpollack.loopy.session.SessionCommand;
import io.github.markpollack.loopy.session.SessionStore;
import io.github.markpollack.loopy.tui.ChatScreen;
import io.github.markpollack.loopy.tui.LogoScreen;

import org.springframework.ai.chat.model.ChatModel;

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
public class LoopyCommand implements Callable<Integer> {

	private final @Nullable ChatModel chatModel;

	@Option(names = { "-d", "--directory" }, description = "Working directory (default: current)")
	private Path directory;

	@Option(names = { "-m", "--model" }, description = "Model name")
	private String model;

	@Option(names = { "-t", "--max-turns" }, description = "Max agent turns (default: 20)")
	private Integer maxTurns;

	@Option(names = { "-p", "--print" }, description = "Single-shot prompt (print mode)")
	private String prompt;

	@Option(names = { "--provider" }, description = "AI provider: anthropic (default), openai, google-genai")
	private String provider;

	@Option(names = { "--base-url" }, description = "Custom API base URL (for local models, vLLM, LM Studio)")
	private String baseUrl;

	@Option(names = { "--debug" }, description = "Show verbose agent activity (turns, tool calls, cost) on stderr")
	private boolean debug;

	@Option(names = { "--repl" }, description = "REPL mode (readline loop)")
	private boolean repl;

	public LoopyCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public Integer call() {
		// Provide a basic TerminalInfo for lipgloss styling in non-TUI modes.
		// TUI mode (bubbletea Program) overwrites this with the real JLine provider.
		TerminalInfo.provide(() -> new TerminalInfo(System.console() != null, null));
		configureDevLogging();
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
		Run journalRun = null;
		MiniAgent agent = null;

		if (this.chatModel != null) {
			try {
				journalRun = startJournalRun();
				agent = createAgent(this.chatModel, true, false, journalRun);
			}
			catch (Exception ex) {
				System.err.println("Error creating agent: " + ex.getMessage());
				if (journalRun != null) {
					journalRun.fail(ex);
					journalRun.close();
				}
				return 1;
			}
		}

		// Build slash command registry
		String activeProvider = this.provider != null ? this.provider : "anthropic";
		final MiniAgent finalAgent = agent;
		ModelCommand modelCommand = new ModelCommand(activeProvider,
				this.model != null ? this.model : defaultModelFor(activeProvider));
		SlashCommandRegistry registry = createCommandRegistry(this.chatModel, modelCommand);
		Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));
		CommandContext ctx = new CommandContext(workDir, finalAgent != null ? finalAgent::clearSession : () -> {
		}, finalAgent != null ? finalAgent::setModelOverride : model -> {
		}, finalAgent != null ? prompt -> finalAgent.run(prompt).output() : prompt -> "Agent not available");

		// Register session command if agent has session memory
		var sessionStore = new SessionStore();
		if (finalAgent != null && finalAgent.hasSessionMemory()) {
			String sessionProvider = activeProvider;
			String sessionModel = this.model != null ? this.model : defaultModelFor(activeProvider);
			registry.register(new SessionCommand(finalAgent.getSessionMemory(), finalAgent.getConversationId(),
					sessionStore, sessionModel, sessionProvider));
		}

		java.util.function.Supplier<Model> chatScreenFactory;
		if (finalAgent != null) {
			chatScreenFactory = () -> new ChatScreen(text -> {
				var result = finalAgent.run(text);
				String output = result.output() != null ? result.output() : "[" + result.status() + "]";
				return output + "\n" + formatCost(result);
			}, (input, ignored) -> registry.dispatch(input, ctx), modelCommand::getActiveModel);
		}
		else {
			// Echo mode when no API key (for testing)
			chatScreenFactory = () -> new ChatScreen((text) -> "You said: " + text,
					(input, ignored) -> registry.dispatch(input, ctx), modelCommand::getActiveModel);
		}

		LogoScreen logo = new LogoScreen(chatScreenFactory);
		Program program = new Program(logo);
		try {
			program.run();
		}
		catch (Exception ex) {
			// Ignore terminal teardown exceptions on exit (e.g. JLine cleanup)
		}

		// Auto-save session on exit if there are messages
		if (finalAgent != null && finalAgent.hasSessionMemory()) {
			autoSave(finalAgent, sessionStore, activeProvider);
		}

		if (journalRun != null) {
			journalRun.close();
		}
		return 0;
	}

	private void autoSave(MiniAgent agent, SessionStore store, String provider) {
		try {
			var messages = agent.getSessionMemory().get(agent.getConversationId());
			if (messages.isEmpty()) {
				return;
			}
			String workDir = (this.directory != null ? this.directory : Path.of(System.getProperty("user.dir")))
				.toAbsolutePath()
				.toString();
			String sessionModel = this.model != null ? this.model : defaultModelFor(provider);
			var metadata = new SessionStore.SessionMetadata(workDir, sessionModel, provider);
			String id = store.save(messages, metadata, null);
			System.err.println("Session auto-saved: " + id);
		}
		catch (Exception ex) {
			// Auto-save failure should not disrupt the user
			System.err.println("Session auto-save failed: " + ex.getMessage());
		}
	}

	private Integer runPrintMode() {
		if (this.chatModel == null) {
			System.err.println("Error: ANTHROPIC_API_KEY environment variable not set");
			return 1;
		}

		if (this.prompt.isBlank()) {
			System.err.println("Error: -p/--print requires a non-empty prompt");
			return 1;
		}

		try (Run journalRun = startJournalRun()) {
			MiniAgent agent = createAgent(this.chatModel, false, true, journalRun);
			System.err.println("Thinking...");
			var result = agent.run(this.prompt);
			System.err.println();
			if (result.output() != null) {
				System.out.println(result.output());
			}
			System.err.println(formatCost(result));
			return (result.isSuccess() || result.isCompletedWithWarning()) ? 0 : 1;
		}
		catch (Exception ex) {
			System.err.println("Error: " + ex.getMessage());
			return 1;
		}
	}

	private Integer runReplMode() {
		if (this.chatModel == null) {
			System.err.println("Error: ANTHROPIC_API_KEY environment variable not set");
			return 1;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				PrintWriter writer = new PrintWriter(System.out, true);
				Run journalRun = startJournalRun()) {

			MiniAgent[] agentRef = { null };
			String activeProvider = this.provider != null ? this.provider : "anthropic";
			ModelCommand modelCommand = new ModelCommand(activeProvider,
					this.model != null ? this.model : defaultModelFor(activeProvider));
			SlashCommandRegistry registry = createCommandRegistry(this.chatModel, modelCommand);
			Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));

			// Build CommandContext once with lazy lambdas — agent may not exist yet
			// when the first slash command runs, so lambdas dereference agentRef at
			// call time rather than construction time
			CommandContext ctx = new CommandContext(workDir, () -> {
				if (agentRef[0] != null)
					agentRef[0].clearSession();
			}, m -> {
				if (agentRef[0] != null)
					agentRef[0].setModelOverride(m);
			}, prompt -> {
				if (agentRef[0] == null) {
					agentRef[0] = createAgent(this.chatModel, true, true, journalRun);
				}
				return agentRef[0].run(prompt).output();
			});

			writer.println("Loopy REPL — type /help for commands, /exit to quit");
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
				if (agentRef[0] == null) {
					try {
						agentRef[0] = createAgent(this.chatModel, true, true, journalRun);
					}
					catch (Exception ex) {
						writer.println("Error creating agent: " + ex.getMessage());
						continue;
					}
				}

				try {
					System.err.println("Thinking...");
					var result = agentRef[0].run(line);
					System.err.println();
					if (result.output() != null) {
						writer.println(result.output());
					}
					else {
						writer.printf("[%s]%n", result.status());
					}
					writer.println(formatCost(result));
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

	private SlashCommandRegistry createCommandRegistry(@Nullable ChatModel chatModel, ModelCommand modelCommand) {
		SlashCommandRegistry registry = new SlashCommandRegistry();
		registry.register(new HelpCommand(registry));
		registry.register(new ClearCommand());
		registry.register(new BtwCommand(chatModel));
		registry.register(modelCommand);
		registry.register(new SkillsCommand());
		registry.register(new ForgeAgentCommand(chatModel));
		registry.register(new BootNewCommand(chatModel));
		registry.register(new BootSetupCommand(chatModel));
		registry.register(new StartersCommand());
		registry.register(new BootAddCommand(chatModel));
		registry.register(new BootModifyCommand());
		registry.register(new QuitCommand());
		return registry;
	}

	private MiniAgent createAgent(ChatModel chatModel, boolean withSession) {
		return createAgent(chatModel, withSession, false, null);
	}

	private MiniAgent createAgent(ChatModel chatModel, boolean withSession, boolean consoleProgress) {
		return createAgent(chatModel, withSession, consoleProgress, null);
	}

	private MiniAgent createAgent(ChatModel chatModel, boolean withSession, boolean consoleProgress,
			@Nullable Run journalRun) {
		Path workDir = this.directory != null ? this.directory : Path.of(System.getProperty("user.dir"));
		int turns = this.maxTurns != null ? this.maxTurns : 20;

		var config = MiniAgentConfig.builder()
			.workingDirectory(workDir)
			.maxTurns(turns)
			.commandTimeout(Duration.ofSeconds(120))
			.build();

		// AGENTS.md + CLAUDE.md auto-injection — append to default system prompt
		String injected = buildContextInjection(workDir, config.systemPrompt());
		if (injected != null) {
			config = config.apply(b -> b.systemPrompt(injected));
		}

		var builder = MiniAgent.builder()
			.config(config)
			.model(chatModel)
			.additionalTools(new BootNewTool(workDir, chatModel), new BootModifyTool(workDir));

		// Apply model override from -m flag (also used for cost estimation)
		if (this.model != null) {
			builder.modelName(this.model);
		}

		// Enable compaction with a cheap model from the same provider
		String compactionModel = resolveCompactionModel();
		if (compactionModel != null) {
			builder.compactionModelName(compactionModel);
		}

		if (withSession) {
			builder.sessionMemory();
		}

		if (this.debug) {
			builder.toolCallListener(new DebugToolCallListener());
			builder.loopListener(new DebugLoopListener());
		}
		else if (consoleProgress) {
			builder.toolCallListener(new ConsoleToolCallListener());
		}

		if (journalRun != null) {
			builder.journalRun(journalRun);
		}

		return builder.build();
	}

	private static String defaultModelFor(String provider) {
		return switch (provider) {
			case "openai" -> "gpt-4.1";
			case "google-genai" -> "gemini-2.5-flash";
			default -> "claude-sonnet-4-6";
		};
	}

	private String resolveCompactionModel() {
		String p = this.provider != null ? this.provider : "anthropic";
		return switch (p) {
			case "anthropic" -> "claude-haiku-4-5-20251001";
			case "openai" -> "gpt-4o-mini";
			case "google-genai" -> "gemini-2.5-flash-lite";
			default -> null;
		};
	}

	private static String formatCost(MiniAgent.MiniAgentResult result) {
		return String.format("tokens: %d/%d | cost: $%.4f", result.inputTokens(), result.outputTokens(),
				result.estimatedCost());
	}

	/**
	 * Configure developer file logging if LOOPY_DEBUG_LOG env var is set.
	 * <p>
	 * Full logback DEBUG output goes to a file — Spring AI advisor chain, token math,
	 * etc. Console logging stays OFF so the TUI is not corrupted.
	 * <p>
	 * Set to a path: {@code LOOPY_DEBUG_LOG=/tmp/loopy.log} Set without value or "1":
	 * uses default {@code ~/.local/state/loopy/logs/loopy-debug.log}
	 */
	private void configureDevLogging() {
		String envVal = System.getenv("LOOPY_DEBUG_LOG");
		if (envVal == null || envVal.isBlank()) {
			return;
		}

		try {
			Path logFile;
			if ("1".equals(envVal) || "true".equalsIgnoreCase(envVal)) {
				Path logDir = Path.of(System.getProperty("user.home"), ".local", "state", "loopy", "logs");
				Files.createDirectories(logDir);
				logFile = logDir.resolve("loopy-debug.log");
			}
			else {
				logFile = Path.of(envVal);
				Files.createDirectories(logFile.getParent());
			}

			var loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

			var encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
			encoder.setContext(loggerContext);
			encoder.setPattern("%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n");
			encoder.start();

			var fileAppender = new ch.qos.logback.core.FileAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
			fileAppender.setContext(loggerContext);
			fileAppender.setFile(logFile.toString());
			fileAppender.setAppend(false);
			fileAppender.setEncoder(encoder);
			fileAppender.start();

			var loopyLogger = loggerContext.getLogger("io.github.markpollack.loopy");
			loopyLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
			loopyLogger.addAppender(fileAppender);

			System.err.println("Dev logging to: " + logFile);
		}
		catch (IOException ex) {
			System.err.println("Warning: could not configure dev logging: " + ex.getMessage());
		}
	}

	private Run startJournalRun() {
		Path journalDir = Path.of(System.getProperty("user.home"), ".local", "state", "loopy", "journal");
		Journal.configure(new JsonFileStorage(journalDir));
		return Journal.run("loopy-session")
			.config("provider", this.provider != null ? this.provider : "anthropic")
			.start();
	}

	/**
	 * Builds the final system prompt by appending AGENTS.md and CLAUDE.md from the
	 * working directory, in that order (CLAUDE.md is more specific, so it wins on
	 * conflicts). Returns null if neither file exists.
	 */
	static String buildContextInjection(Path workDir, String basePrompt) {
		String agentsMd = readFile(workDir.resolve("AGENTS.md"));
		String claudeMd = readFile(workDir.resolve("CLAUDE.md"));
		if (agentsMd == null && claudeMd == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder(basePrompt);
		if (agentsMd != null) {
			sb.append("\n\n## Project Instructions (AGENTS.md)\n").append(agentsMd);
		}
		if (claudeMd != null) {
			sb.append("\n\n## Project Instructions (CLAUDE.md)\n").append(claudeMd);
		}
		return sb.toString();
	}

	private static String readFile(Path path) {
		if (Files.isRegularFile(path)) {
			try {
				return Files.readString(path);
			}
			catch (IOException ex) {
				// Silently ignore — not critical
			}
		}
		return null;
	}

}
