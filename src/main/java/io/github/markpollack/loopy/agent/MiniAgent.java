package io.github.markpollack.loopy.agent;

import io.github.markpollack.journal.Run;
import io.micrometer.observation.ObservationRegistry;
import io.github.markpollack.loopy.agent.callback.AgentCallback;
import io.github.markpollack.loopy.agent.core.ToolCallListener;
import io.github.markpollack.loopy.agent.journal.JournalLoopListener;
import io.github.markpollack.loopy.agent.journal.JournalToolCallListener;
import io.github.markpollack.loopy.agent.loop.AgentLoopAdvisor;
import io.github.markpollack.loopy.agent.loop.AgentLoopListener;
import io.github.markpollack.loopy.agent.loop.AgentLoopTerminatedException;
import io.github.markpollack.loopy.agent.loop.ToolCallObservationHandler;
import io.github.markpollack.loopy.agent.core.LoopState;
import io.github.markpollack.loopy.agent.core.TerminationReason;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ListDirectoryTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.tools.task.TaskOutputTool;
import org.springaicommunity.agent.tools.task.TaskTool;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentReferences;
import org.springaicommunity.agent.tools.task.claude.ClaudeSubagentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.model.tool.DefaultToolCallingManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MiniAgent - A minimal SWE agent leveraging Spring AI's built-in agent loop.
 * <p>
 * Spring AI's ChatClient + ToolCallAdvisor handles the entire tool execution loop. This
 * agent adds: tools, observability wiring, session memory, and a simple API.
 * <p>
 * Features:
 * <ul>
 * <li><strong>Session memory</strong>: Optional multi-turn conversation support</li>
 * <li><strong>Interactive mode</strong>: Enables AskUserQuestionTool for
 * human-in-the-loop</li>
 * <li><strong>Callbacks</strong>: AgentCallback for TUI integration</li>
 * </ul>
 *
 * @see MiniAgentConfig for configuration options
 */
public class MiniAgent {

	private static final Logger log = LoggerFactory.getLogger(MiniAgent.class);

	private final MiniAgentConfig config;

	private final ChatClient chatClient;

	private final List<ToolCallback> tools;

	private final ToolCallObservationHandler observationHandler;

	private final CountingToolCallListener countingListener;

	private final AgentLoopAdvisor agentLoopAdvisor;

	private final ChatMemory sessionMemory;

	private final boolean interactive;

	private final String conversationId;

	/** Underlying model — used for grace turns when the advisor loop has terminated. */
	private final ChatModel model;

	/** Per-request model override — null means use the ChatModel's default. */
	private volatile String modelOverride;

	private MiniAgent(Builder builder) {
		this.config = builder.config;
		this.model = builder.model;
		this.sessionMemory = builder.sessionMemory;
		this.interactive = builder.interactive;
		this.conversationId = builder.conversationId != null ? builder.conversationId : "default";

		// Create tools - mix of spring-ai-agent-utils and harness-tools
		// Use harness-tools BashTool instead of ShellTools to avoid overly verbose tool
		// descriptions
		// Pass workingDirectory to tools that support it so they operate within the
		// sandbox context

		// Tools with @Tool annotated methods - convert via ToolCallbacks.from()
		List<Object> annotatedToolObjects = new ArrayList<>();
		annotatedToolObjects.add(FileSystemTools.builder().build());
		annotatedToolObjects.add(new BashTool(config.workingDirectory(), config.commandTimeout()));
		annotatedToolObjects.add(GlobTool.builder().workingDirectory(config.workingDirectory()).build());
		annotatedToolObjects.add(GrepTool.builder().workingDirectory(config.workingDirectory()).build());
		annotatedToolObjects.add(ListDirectoryTool.builder().workingDirectory(config.workingDirectory()).build());
		annotatedToolObjects.add(new SubmitTool());
		annotatedToolObjects.add(TodoWriteTool.builder().build());

		// Add AskUserQuestionTool if interactive mode and callback provided
		if (interactive && builder.agentCallback != null) {
			annotatedToolObjects.add(AskUserQuestionTool.builder()
				.questionHandler(questions -> builder.agentCallback.onQuestion(questions))
				.build());
		}

		// Add web search and fetch tools if BRAVE_API_KEY is available
		String braveApiKey = System.getenv("BRAVE_API_KEY");
		if (braveApiKey != null && !braveApiKey.isBlank()) {
			annotatedToolObjects.add(BraveWebSearchTool.builder(braveApiKey).build());
			var fetchClient = ChatClient.builder(builder.model).build();
			annotatedToolObjects.add(SmartWebFetchTool.builder(fetchClient).build());
		}

		// Tools that directly implement ToolCallback - add directly to callback list
		List<ToolCallback> directCallbacks = new ArrayList<>();

		// Add TaskTool for sub-agent delegation (returns ToolCallback directly)
		if (!builder.disabledTools.contains("Task")) {
			var taskRepository = new DefaultTaskRepository();

			// Multi-model routing: register tier aliases so subagent definitions
			// using model: haiku / sonnet route to appropriately-priced models.
			// Uses the same ChatModel with a per-request ChatOptions model override —
			// no separate API credentials needed.
			var subagentTypeBuilder = ClaudeSubagentType.builder()
				.chatClientBuilder("default", ChatClient.builder(builder.model));
			if (builder.compactionModelName != null) {
				subagentTypeBuilder.chatClientBuilder("haiku", ChatClient.builder(builder.model)
					.defaultOptions(ChatOptions.builder().model(builder.compactionModelName).build()));
			}
			if (builder.modelName != null) {
				subagentTypeBuilder.chatClientBuilder("sonnet", ChatClient.builder(builder.model)
					.defaultOptions(ChatOptions.builder().model(builder.modelName).build()));
			}

			// Propagate skills directories so subagents share the same knowledge base
			// as the main agent
			var subagentProjectSkillsDir = config.workingDirectory().resolve(".claude/skills");
			if (java.nio.file.Files.isDirectory(subagentProjectSkillsDir)) {
				subagentTypeBuilder.skillsDirectories(subagentProjectSkillsDir.toString());
			}
			var subagentGlobalSkillsDir = java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "skills");
			if (java.nio.file.Files.isDirectory(subagentGlobalSkillsDir)) {
				subagentTypeBuilder.skillsDirectories(subagentGlobalSkillsDir.toString());
			}

			// Build TaskTool with custom subagent definitions from .claude/agents/
			var taskToolBuilder = TaskTool.builder()
				.taskRepository(taskRepository)
				.subagentTypes(subagentTypeBuilder.build());

			// Project-level custom agents: .claude/agents/*.md in working directory.
			// Filter to files with valid YAML frontmatter (name: + description:) —
			// Claude Code native agent files use plain markdown headings and would
			// cause ClaudeSubagentDefinition.getName() to throw NPE.
			var projectAgentsDir = config.workingDirectory().resolve(".claude/agents");
			if (java.nio.file.Files.isDirectory(projectAgentsDir)) {
				var projectRefs = ClaudeSubagentReferences.fromRootDirectory(projectAgentsDir.toString())
					.stream()
					.filter(ref -> hasNamedFrontmatter(ref.uri()))
					.toList();
				if (!projectRefs.isEmpty()) {
					taskToolBuilder.subagentReferences(projectRefs);
					log.debug("Loaded {} custom subagent(s) from {}", projectRefs.size(), projectAgentsDir);
				}
			}

			// Global custom agents: ~/.claude/agents/*.md — same filter applies
			var globalAgentsDir = java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "agents");
			if (java.nio.file.Files.isDirectory(globalAgentsDir)) {
				var globalRefs = ClaudeSubagentReferences.fromRootDirectory(globalAgentsDir.toString())
					.stream()
					.filter(ref -> hasNamedFrontmatter(ref.uri()))
					.toList();
				if (!globalRefs.isEmpty()) {
					taskToolBuilder.subagentReferences(globalRefs);
					log.debug("Loaded {} global subagent(s) from {}", globalRefs.size(), globalAgentsDir);
				}
			}

			directCallbacks.add(taskToolBuilder.build());

			// TaskOutputTool shares the same repository — required for background task
			// result retrieval
			if (!builder.disabledTools.contains("TaskOutput")) {
				directCallbacks.add(TaskOutputTool.builder().taskRepository(taskRepository).build());
			}
		}

		// Add SkillsTool for domain knowledge discovery (progressive disclosure)
		// Three sources: filesystem directories, classpath JARs (SkillsJars convention)
		if (!builder.disabledTools.contains("Skills")) {
			var skillsBuilder = SkillsTool.builder();
			boolean hasSkills = false;

			// Project-level skills: .claude/skills/ in working directory
			var projectSkillsDir = config.workingDirectory().resolve(".claude/skills");
			if (java.nio.file.Files.isDirectory(projectSkillsDir)) {
				skillsBuilder.addSkillsDirectory(projectSkillsDir.toString());
				hasSkills = true;
			}

			// Global skills: ~/.claude/skills/
			var globalSkillsDir = java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "skills");
			if (java.nio.file.Files.isDirectory(globalSkillsDir)) {
				skillsBuilder.addSkillsDirectory(globalSkillsDir.toString());
				hasSkills = true;
			}

			// Classpath skills: SkillsJars on the classpath (Maven dependencies)
			// Two conventions: META-INF/skills/ and META-INF/resources/skills/
			for (String prefix : List.of("META-INF/skills", "META-INF/resources/skills")) {
				try {
					var resource = new org.springframework.core.io.ClassPathResource(prefix);
					skillsBuilder.addSkillsResource(resource);
					hasSkills = true;
				}
				catch (Exception ex) {
					// No skills at this prefix — expected when no SkillsJars on classpath
					log.debug("No classpath skills at {}: {}", prefix, ex.getMessage());
				}
			}

			if (hasSkills) {
				try {
					directCallbacks.add(skillsBuilder.build());
				}
				catch (IllegalArgumentException ex) {
					// No SKILL.md files found in any source — skip silently
					log.debug("SkillsTool skipped: {}", ex.getMessage());
				}
			}
		}

		// Inject additional @Tool-annotated objects registered via
		// Builder.additionalTools()
		annotatedToolObjects.addAll(builder.additionalToolObjects);

		// Convert @Tool annotated objects to ToolCallbacks and merge with direct
		// callbacks
		var annotatedCallbacks = ToolCallbacks.from(annotatedToolObjects.toArray());
		var allCallbacks = new ArrayList<>(Arrays.asList(annotatedCallbacks));
		allCallbacks.addAll(directCallbacks);

		// Filter out disabled tools by name
		if (!builder.disabledTools.isEmpty()) {
			allCallbacks.removeIf(cb -> builder.disabledTools.contains(cb.getToolDefinition().name()));
		}
		this.tools = List.copyOf(allCallbacks);

		// Wrap listener in counting listener for toolCallsExecuted tracking
		ToolCallListener baseListener = builder.toolCallListener != null ? builder.toolCallListener
				: new LoggingToolCallListener();
		if (builder.journalRun != null) {
			baseListener = new CompositeToolCallListener(baseListener, new JournalToolCallListener(builder.journalRun));
		}
		this.countingListener = new CountingToolCallListener(baseListener);

		// Wire observability: ObservationRegistry -> ToolCallObservationHandler ->
		// ToolCallListeners (counting + hash tracking for stuck detection)
		var hashTracker = new ToolCallHashTracker();
		this.observationHandler = ToolCallObservationHandler.of(List.of(countingListener, hashTracker));
		var registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(observationHandler);

		// Create ChatClient with advisors
		// Case-insensitive tool resolver: smaller models (Qwen, etc.) often capitalize
		// tool names (e.g., "Bash" instead of "bash", "LS" instead of non-existent tool).
		var toolsByLowerName = new java.util.HashMap<String, ToolCallback>();
		for (var cb : allCallbacks) {
			toolsByLowerName.put(cb.getToolDefinition().name().toLowerCase(), cb);
		}

		var toolCallingManager = DefaultToolCallingManager.builder()
			.observationRegistry(registry)
			.toolCallbackResolver(toolName -> resolveToolCallback(toolName, toolsByLowerName))
			.toolExecutionExceptionProcessor(ex -> processToolError(ex))
			.build();

		// Build AgentLoopAdvisor with optional listener bridge
		var advisorBuilder = AgentLoopAdvisor.builder()
			.toolCallingManager(toolCallingManager)
			.maxTurns(config.maxTurns())
			.toolCallHashTracker(hashTracker);

		if (builder.timeout != null) {
			advisorBuilder.timeout(builder.timeout);
		}

		if (builder.modelName != null) {
			advisorBuilder.modelName(builder.modelName);
		}

		if (builder.agentCallback != null) {
			advisorBuilder.listener(new CallbackLoopListener(builder.agentCallback));
		}

		if (builder.journalRun != null) {
			advisorBuilder.listener(new JournalLoopListener(builder.journalRun));
		}

		for (var loopListener : builder.loopListeners) {
			advisorBuilder.listener(loopListener);
		}

		if (builder.compactionModelName != null) {
			advisorBuilder.chatModel(builder.model);
			advisorBuilder.compactionModelName(builder.compactionModelName);
		}
		advisorBuilder.contextLimit(builder.contextLimit);
		advisorBuilder.compactionThreshold(builder.compactionThreshold);

		var toolCallAdvisor = advisorBuilder.build();
		this.agentLoopAdvisor = toolCallAdvisor;

		// Build ChatClient with optional memory advisor
		// Note: defaultToolContext is required for tools that use ToolContext (e.g.,
		// FileSystemTools)
		// tools list already contains ToolCallback instances
		var chatClientBuilder = ChatClient.builder(builder.model)
			.defaultAdvisors(toolCallAdvisor)
			.defaultToolCallbacks(tools.toArray(new ToolCallback[0]))
			.defaultToolContext(Map.of("agentId", "mini-agent"));

		if (sessionMemory != null) {
			var memoryAdvisor = MessageChatMemoryAdvisor.builder(sessionMemory).conversationId(conversationId).build();
			chatClientBuilder.defaultAdvisors(memoryAdvisor);
		}

		this.chatClient = chatClientBuilder.build();

		// Initialise model override from builder if provided
		this.modelOverride = builder.modelName;
	}

	/**
	 * Run the agent with the given task (single-task mode).
	 * <p>
	 * If session memory is configured, the conversation history is preserved across
	 * multiple run() calls.
	 */
	public MiniAgentResult run(String task) {
		log.info("MiniAgent starting: {}", truncate(task, 80));
		countingListener.reset();
		observationHandler.setContext("mini-agent", 1);

		try {
			// Include working directory in system prompt so LLM uses correct paths
			String systemPromptWithWorkdir = config.systemPrompt() + "\n\nYour working directory is: "
					+ config.workingDirectory().toAbsolutePath();

			var requestSpec = chatClient.prompt().system(systemPromptWithWorkdir).user(task);
			String override = this.modelOverride;
			if (override != null) {
				requestSpec = requestSpec.options(ChatOptions.builder().model(override).build());
			}

			ChatResponse response = requestSpec.call().chatResponse();

			String output = extractText(response);
			int toolCalls = countingListener.getToolCallCount();

			// Use accumulated state from advisor (covers all turns, not just the last)
			LoopState state = agentLoopAdvisor.getCurrentState();
			long totalTokens = state != null ? state.totalTokensUsed() : extractTokens(response);
			long inputTokens = state != null ? state.inputTokensUsed() : 0;
			long outputTokens = state != null ? state.outputTokensUsed() : 0;
			double cost = state != null ? state.estimatedCost() : 0.0;
			int turns = state != null ? state.currentTurn() : 1;

			log.info("MiniAgent completed: {} tokens ({}in/{}out), {} tool calls, cost=${}", totalTokens, inputTokens,
					outputTokens, toolCalls, String.format("%.4f", cost));

			return new MiniAgentResult("COMPLETED", output, turns, toolCalls, totalTokens, inputTokens, outputTokens,
					cost);

		}
		catch (AgentLoopTerminatedException e) {
			var state = e.getState();
			log.warn("MiniAgent terminated: {} at turn {}", e.getReason(), state != null ? state.currentTurn() : 0);
			int toolCalls = countingListener.getToolCallCount();
			long totalTokens = state != null ? state.totalTokensUsed() : 0;
			long inputTokens = state != null ? state.inputTokensUsed() : 0;
			long outputTokens = state != null ? state.outputTokensUsed() : 0;
			double cost = state != null ? state.estimatedCost() : 0.0;
			int turns = state != null ? state.currentTurn() : 0;

			if (e.getReason() == TerminationReason.MAX_TURNS_REACHED) {
				// Grace turn: give the model one chance to summarize before returning
				String graceOutput = tryGraceTurn(task, e.getPartialOutput(), config.systemPrompt());
				if (graceOutput != null) {
					log.info("Grace turn succeeded after max turns");
					return new MiniAgentResult("COMPLETED_WITH_WARNING", graceOutput, turns, toolCalls, totalTokens,
							inputTokens, outputTokens, cost);
				}
			}

			String status = switch (e.getReason()) {
				case MAX_TURNS_REACHED -> "TURN_LIMIT_REACHED";
				case TIMEOUT -> "TIMEOUT";
				case COST_LIMIT_EXCEEDED -> "COST_LIMIT_EXCEEDED";
				case STUCK_DETECTED -> "STUCK";
				case EXTERNAL_SIGNAL -> "ABORTED";
				default -> "TERMINATED";
			};
			return new MiniAgentResult(status, e.getPartialOutput(), turns, toolCalls, totalTokens, inputTokens,
					outputTokens, cost);
		}
		catch (Exception e) {
			// Catch-all for unhandled errors (e.g., unknown tool name, model quirks).
			// Return partial results instead of crashing.
			log.warn("MiniAgent failed with unexpected error: {}", e.getMessage(), e);
			var state = agentLoopAdvisor.getCurrentState();
			int toolCalls = countingListener.getToolCallCount();
			return new MiniAgentResult("ERROR", e.getMessage(), state != null ? state.currentTurn() : 0, toolCalls,
					state != null ? state.totalTokensUsed() : 0, state != null ? state.inputTokensUsed() : 0,
					state != null ? state.outputTokensUsed() : 0, state != null ? state.estimatedCost() : 0.0);
		}
	}

	/**
	 * Chat with the agent (multi-turn interactive mode).
	 * <p>
	 * Unlike run(), this method is designed for interactive TUI use: - Callbacks are
	 * invoked for thinking, tool calls, and responses - Session memory preserves
	 * conversation across calls - Questions are routed to the callback for user
	 * interaction
	 * @param message User message
	 * @param callback Callback for events (must be same as builder callback)
	 * @return Agent result
	 */
	public MiniAgentResult chat(String message, AgentCallback callback) {
		// Note: onThinking is called by CallbackLoopListener.onTurnStarted()
		MiniAgentResult result = run(message);
		if (callback != null) {
			callback.onComplete();
		}
		return result;
	}

	/**
	 * Switch the model used for inference. Applies as a per-request {@link ChatOptions}
	 * override — the underlying {@link ChatModel} and advisors remain unchanged.
	 * @param model model identifier (e.g. "claude-haiku-4-5-20251001"), or null to revert
	 * to the ChatModel's default
	 */
	public void setModelOverride(String model) {
		this.modelOverride = model;
		log.debug("Model override set to: {}", model);
	}

	/**
	 * Makes one tool-free LLM call asking the model to summarize after max turns. Returns
	 * the grace output, or null if the call fails or returns blank.
	 */
	String tryGraceTurn(String task, String partialOutput, String systemPrompt) {
		return tryGraceTurn(model, task, partialOutput, systemPrompt);
	}

	/**
	 * Makes one tool-free LLM call asking the model to summarize after max turns. Returns
	 * the grace output, or null if the call fails or returns blank.
	 */
	static String tryGraceTurn(ChatModel chatModel, String task, String partialOutput, String systemPrompt) {
		try {
			log.info("Attempting grace turn after max turns reached");
			var messages = new ArrayList<org.springframework.ai.chat.messages.Message>();
			messages.add(new SystemMessage(systemPrompt));
			messages.add(new UserMessage(task));
			if (partialOutput != null && !partialOutput.isBlank()) {
				messages.add(new AssistantMessage(partialOutput));
			}
			messages.add(new UserMessage(
					"You have reached the turn limit. Please provide your best answer based on your progress so far. Be concise."));
			ChatResponse response = chatModel.call(new Prompt(messages));
			if (response == null || response.getResult() == null) {
				return null;
			}
			String text = response.getResult().getOutput().getText();
			return (text != null && !text.isBlank()) ? text : null;
		}
		catch (Exception ex) {
			log.warn("Grace turn failed: {}", ex.getMessage());
			return null;
		}
	}

	/**
	 * Clear session memory, starting a fresh conversation.
	 */
	public void clearSession() {
		if (sessionMemory != null) {
			sessionMemory.clear(conversationId);
			log.debug("Session cleared for conversation: {}", conversationId);
		}
	}

	/**
	 * Check if session memory is enabled.
	 */
	public boolean hasSessionMemory() {
		return sessionMemory != null;
	}

	/**
	 * Check if interactive mode is enabled.
	 */
	public boolean isInteractive() {
		return interactive;
	}

	/**
	 * Returns the names of all registered tools.
	 */
	public List<String> toolNames() {
		return tools.stream().map(cb -> cb.getToolDefinition().name()).toList();
	}

	private long extractTokens(ChatResponse r) {
		if (r == null || r.getMetadata() == null || r.getMetadata().getUsage() == null)
			return 0;
		var t = r.getMetadata().getUsage().getTotalTokens();
		return t != null ? t : 0;
	}

	private String extractText(ChatResponse r) {
		return r != null && r.getResult() != null ? r.getResult().getOutput().getText() : null;
	}

	private String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
	}

	/**
	 * Returns true if the markdown file at {@code filePath} has a YAML frontmatter block
	 * that contains both {@code name:} and {@code description:} keys — the minimum
	 * required by
	 * {@link org.springaicommunity.agent.tools.task.claude.ClaudeSubagentDefinition}.
	 * <p>
	 * Claude Code's native agent files use plain markdown headings with no frontmatter;
	 * this filter excludes them rather than letting the build fail with an NPE.
	 */
	private static boolean hasNamedFrontmatter(String filePath) {
		try {
			String content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath));
			if (!content.startsWith("---")) {
				return false;
			}
			int closingDashes = content.indexOf("---", 3);
			if (closingDashes < 0) {
				return false;
			}
			String frontmatter = content.substring(3, closingDashes);
			return frontmatter.contains("name:") && frontmatter.contains("description:");
		}
		catch (Exception ex) {
			log.debug("Skipping agent file {} — could not read: {}", filePath, ex.getMessage());
			return false;
		}
	}

	// --- Builder ---

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private MiniAgentConfig config;

		private ChatModel model;

		private ChatMemory sessionMemory;

		private boolean interactive = false;

		private AgentCallback agentCallback;

		private ToolCallListener toolCallListener;

		private String conversationId;

		private String modelName;

		private Set<String> disabledTools = Set.of();

		private java.time.Duration timeout;

		private String compactionModelName;

		private int contextLimit = 200_000;

		private double compactionThreshold = 0.5;

		private Run journalRun;

		private final List<AgentLoopListener> loopListeners = new ArrayList<>();

		private final List<Object> additionalToolObjects = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Set the agent configuration (required).
		 */
		public Builder config(MiniAgentConfig config) {
			this.config = config;
			return this;
		}

		/**
		 * Set the chat model (required).
		 */
		public Builder model(ChatModel model) {
			this.model = model;
			return this;
		}

		/**
		 * Enable session memory for multi-turn conversations.
		 * <p>
		 * If null (default), each run() is independent with no history. If provided,
		 * conversation history is preserved across calls.
		 */
		public Builder sessionMemory(ChatMemory sessionMemory) {
			this.sessionMemory = sessionMemory;
			return this;
		}

		/**
		 * Enable session memory with default in-memory implementation.
		 */
		public Builder sessionMemory() {
			this.sessionMemory = MessageWindowChatMemory.builder().build();
			return this;
		}

		/**
		 * Enable interactive mode with AskUserQuestionTool.
		 * <p>
		 * When true and agentCallback is provided, the agent can ask the user questions
		 * during execution via onQuestion().
		 */
		public Builder interactive(boolean interactive) {
			this.interactive = interactive;
			return this;
		}

		/**
		 * Set callback for agent events (TUI integration).
		 * <p>
		 * Required for interactive mode to handle questions.
		 */
		public Builder agentCallback(AgentCallback agentCallback) {
			this.agentCallback = agentCallback;
			return this;
		}

		/**
		 * Set callback for tool call events.
		 */
		public Builder toolCallListener(ToolCallListener toolCallListener) {
			this.toolCallListener = toolCallListener;
			return this;
		}

		/**
		 * Set conversation ID for session memory.
		 * <p>
		 * Defaults to "default" if not specified.
		 */
		public Builder conversationId(String conversationId) {
			this.conversationId = conversationId;
			return this;
		}

		/**
		 * Override the agent loop timeout (defaults to 10 minutes).
		 */
		public Builder timeout(java.time.Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Disable specific tools by name (e.g., "Task", "TodoWrite"). Disabled tools will
		 * not be registered with the agent.
		 */
		public Builder disabledTools(Set<String> toolNames) {
			this.disabledTools = toolNames;
			return this;
		}

		/**
		 * Set the model name used for context compaction. Uses the primary chat model
		 * with a per-request model override. If null (default), compaction is disabled.
		 */
		public Builder compactionModelName(String compactionModelName) {
			this.compactionModelName = compactionModelName;
			return this;
		}

		/**
		 * Set the context token limit for compaction threshold calculation (default
		 * 200,000).
		 */
		public Builder contextLimit(int contextLimit) {
			this.contextLimit = contextLimit;
			return this;
		}

		/**
		 * Set the fraction of context limit that triggers compaction (default 0.5).
		 */
		public Builder compactionThreshold(double compactionThreshold) {
			this.compactionThreshold = compactionThreshold;
			return this;
		}

		/**
		 * Set a journal Run to record tool calls and loop events. When set, a
		 * {@link JournalToolCallListener} and {@link JournalLoopListener} are
		 * automatically wired in alongside any existing listeners.
		 */
		public Builder journalRun(Run journalRun) {
			this.journalRun = journalRun;
			return this;
		}

		/**
		 * Register additional {@code @Tool}-annotated objects with the agent. Use this to
		 * inject domain-specific tools (e.g. boot scaffolding, CI generation) without
		 * creating a dependency from the agent layer to those packages.
		 */
		public Builder additionalTools(Object... tools) {
			this.additionalToolObjects.addAll(Arrays.asList(tools));
			return this;
		}

		/**
		 * Add a loop listener for agent loop events (turn tracking, completion).
		 */
		public Builder loopListener(AgentLoopListener listener) {
			this.loopListeners.add(listener);
			return this;
		}

		/**
		 * Set the model name for accurate cost estimation.
		 * <p>
		 * Uses prefix matching (e.g. "claude-haiku-4-5-20251001" matches "claude-haiku"
		 * rates). Defaults to Sonnet pricing if not specified.
		 */
		public Builder modelName(String modelName) {
			this.modelName = modelName;
			return this;
		}

		public MiniAgent build() {
			if (config == null) {
				throw new IllegalStateException("config is required");
			}
			if (model == null) {
				throw new IllegalStateException("model is required");
			}
			if (interactive && agentCallback == null) {
				log.warn("Interactive mode enabled but no agentCallback provided - questions will not be handled");
			}
			return new MiniAgent(this);
		}

	}

	/**
	 * Bridges AgentLoopListener events to AgentCallback.
	 */
	private static class CallbackLoopListener implements AgentLoopListener {

		private final AgentCallback callback;

		CallbackLoopListener(AgentCallback callback) {
			this.callback = callback;
		}

		@Override
		public void onLoopStarted(String runId, String userMessage) {
			// AgentCallback doesn't have a direct equivalent
		}

		@Override
		public void onTurnStarted(String runId, int turn) {
			callback.onThinking();
		}

		@Override
		public void onTurnCompleted(String runId, int turn, TerminationReason reason) {
			// Handled in onLoopCompleted
		}

		@Override
		public void onLoopCompleted(String runId, LoopState state, TerminationReason reason) {
			callback.onComplete();
		}

		@Override
		public void onLoopFailed(String runId, LoopState state, Throwable error) {
			callback.onError(error);
		}

	}

	/**
	 * Result of a MiniAgent execution.
	 *
	 * @param status Status of the execution (COMPLETED, COMPLETED_WITH_WARNING,
	 * TURN_LIMIT_REACHED, FAILED)
	 * @param output The agent's final output (may be partial if turn limit reached)
	 * @param turnsCompleted Number of turns (LLM call + tool execution cycles) completed
	 * @param toolCallsExecuted Number of tool calls executed across all turns
	 * @param totalTokens Total tokens used (input + output)
	 * @param inputTokens Input (prompt) tokens used
	 * @param outputTokens Output (generation) tokens used
	 * @param estimatedCost Estimated cost in dollars (model-aware)
	 */
	public record MiniAgentResult(String status, String output, int turnsCompleted, int toolCallsExecuted,
			long totalTokens, long inputTokens, long outputTokens, double estimatedCost) {
		public boolean isSuccess() {
			return "COMPLETED".equals(status);
		}

		public boolean isFailure() {
			return "FAILED".equals(status);
		}

		public boolean isTurnLimitReached() {
			return "TURN_LIMIT_REACHED".equals(status);
		}

		public boolean isCompletedWithWarning() {
			return "COMPLETED_WITH_WARNING".equals(status);
		}
	}

	/**
	 * Tool for submitting the final answer and completing the task.
	 * <p>
	 * Using returnDirect=true means the result is returned directly to the user without
	 * going back to the model, effectively terminating the agent loop.
	 */
	private static class SubmitTool {

		private static final Logger log = LoggerFactory.getLogger(SubmitTool.class);

		@org.springframework.ai.tool.annotation.Tool(name = "Submit",
				description = "Submit your final answer when the task is complete. This ends the conversation.",
				returnDirect = true)
		public String submit(@org.springframework.ai.tool.annotation.ToolParam(
				description = "The final answer or result of the task") String answer) {
			log.info("Task submitted with answer: {}",
					answer != null && answer.length() > 100 ? answer.substring(0, 100) + "..." : answer);
			return answer;
		}

	}

	/**
	 * Resolves a tool callback by name, case-insensitively. Falls back to bash for
	 * unknown tool names so the loop continues rather than crashing.
	 */
	static ToolCallback resolveToolCallback(String toolName, Map<String, ToolCallback> toolsByLowerName) {
		var cb = toolsByLowerName.get(toolName.toLowerCase());
		if (cb != null) {
			return cb;
		}
		log.warn("Unknown tool '{}', falling back to bash. Available: {}", toolName, toolsByLowerName.keySet());
		return toolsByLowerName.get("bash");
	}

	/**
	 * Converts a tool execution exception into an error string returned to the model
	 * instead of crashing the agent loop.
	 */
	static String processToolError(Exception ex) {
		String msg = ex.getMessage();
		if (msg == null || msg.isBlank()) {
			Throwable cause = ex.getCause();
			msg = "Tool error: " + (cause != null ? cause.getClass().getSimpleName() : ex.getClass().getSimpleName());
		}
		log.warn("Tool execution error (returning to model): {}", msg);
		return msg;
	}

}
