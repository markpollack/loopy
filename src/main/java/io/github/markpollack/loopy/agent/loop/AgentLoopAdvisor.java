package io.github.markpollack.loopy.agent.loop;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.loopy.agent.ToolCallHashTracker;
import io.github.markpollack.loopy.agent.core.LoopState;
import io.github.markpollack.loopy.agent.core.TerminationReason;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified agent loop advisor with comprehensive control features.
 * <p>
 * Consolidates turn limiting, cost tracking, timeout, abort signals, stuck detection, and
 * listeners into a single advisor that leverages Spring AI's recursive tool calling.
 * <p>
 * This advisor extends {@link ToolCallAdvisor} and hooks into its recursive tool calling
 * loop via:
 * <ul>
 * <li>{@code doInitializeLoop()} - Reset state, start timer, notify listeners</li>
 * <li>{@code doBeforeCall()} - Check termination conditions before each LLM call</li>
 * <li>{@code doAfterCall()} - Track metrics, notify listeners</li>
 * </ul>
 * <p>
 * <strong>Usage:</strong> <pre>{@code
 * var advisor = AgentLoopAdvisor.builder()
 *     .toolCallingManager(manager)
 *     .maxTurns(20)
 *     .timeout(Duration.ofMinutes(5))
 *     .costLimit(1.0)
 *     .listener(myListener)
 *     .build();
 *
 * var chatClient = ChatClient.builder(model)
 *     .defaultAdvisors(advisor)
 *     .build();
 * }</pre>
 *
 * @see AgentLoopConfig for configuration options
 * @see AgentLoopListener for event handling
 */
public class AgentLoopAdvisor extends ToolCallAdvisor {

	private static final Logger log = LoggerFactory.getLogger(AgentLoopAdvisor.class);

	// Per-model pricing: input $/MTok, output $/MTok
	private static final Map<String, double[]> MODEL_PRICING = Map.of("claude-haiku", new double[] { 0.80, 4.00 },
			"claude-sonnet", new double[] { 3.00, 15.00 }, "claude-opus", new double[] { 15.00, 75.00 });

	private static final double[] DEFAULT_PRICING = new double[] { 3.00, 15.00 };

	static final String COMPACTION_PROMPT = """
			Summarize the following conversation history into two sections:

			## Status
			What has been accomplished and what remains to be done.

			## Learnings
			Key facts, decisions, patterns discovered, and mistakes to avoid.

			Be dense and specific. Preserve file paths, error messages, and technical details.""";

	static final double DEFAULT_COMPACTION_THRESHOLD = 0.5;

	static final int DEFAULT_CONTEXT_LIMIT = 200_000;

	private static final double PRESERVE_FRACTION = 0.3;

	private final AgentLoopConfig config;

	private final List<AgentLoopListener> listeners;

	private final AtomicBoolean abortSignal;

	private final Path workingDirectory;

	private final String modelName;

	private final @Nullable ChatModel chatModel;

	private final @Nullable String compactionModelName;

	private final int contextLimit;

	private final double compactionThreshold;

	// Thread-local state for concurrent safety
	private final ThreadLocal<LoopState> loopState = new ThreadLocal<>();

	private final ThreadLocal<String> userMessage = new ThreadLocal<>();

	private final @Nullable ToolCallHashTracker toolCallHashTracker;

	protected AgentLoopAdvisor(Builder builder) {
		super(builder.toolCallingManager, builder.advisorOrder, true);
		this.config = new AgentLoopConfig(builder.maxTurns, builder.timeout, builder.costLimit, builder.stuckThreshold,
				0 // jury always disabled
		);
		this.listeners = new ArrayList<>(builder.listeners);
		this.abortSignal = new AtomicBoolean(false);
		this.workingDirectory = builder.workingDirectory;
		this.modelName = builder.modelName;
		this.chatModel = builder.chatModel;
		this.compactionModelName = builder.compactionModelName;
		this.contextLimit = builder.contextLimit;
		this.compactionThreshold = builder.compactionThreshold;
		this.toolCallHashTracker = builder.toolCallHashTracker;
	}

	@Override
	public String getName() {
		return "Agent Loop Advisor";
	}

	// --- ToolCallAdvisor hooks ---

	@Override
	protected ChatClientRequest doInitializeLoop(ChatClientRequest request, CallAdvisorChain chain) {
		// Initialize fresh state for this run
		String runId = UUID.randomUUID().toString();
		LoopState initialState = LoopState.initial(runId);
		loopState.set(initialState);

		// Extract and store user message for listeners
		String message = extractUserMessage(request);
		userMessage.set(message);

		// Reset abort signal
		abortSignal.set(false);

		log.debug("Loop initialized: runId={}, maxTurns={}, timeout={}", runId, config.maxTurns(), config.timeout());

		// Notify listeners
		notifyLoopStarted(runId, message);

		return super.doInitializeLoop(request, chain);
	}

	@Override
	protected ChatClientRequest doBeforeCall(ChatClientRequest request, CallAdvisorChain chain) {
		LoopState state = loopState.get();
		if (state == null) {
			// Shouldn't happen, but be defensive
			log.warn("Loop state not initialized, creating new state");
			state = LoopState.initial(UUID.randomUUID().toString());
			loopState.set(state);
		}

		// Compact messages if context is getting large
		if (shouldCompact(request)) {
			request = compactMessages(request);
		}

		// Check termination conditions BEFORE each LLM call
		checkAbortSignal(state);
		checkTimeout(state);
		checkCostLimit(state);
		checkMaxTurns(state);

		// Notify turn starting
		notifyTurnStarted(state.runId(), state.currentTurn());

		log.debug("Turn {} starting for run {}", state.currentTurn() + 1, state.runId());

		return super.doBeforeCall(request, chain);
	}

	@Override
	protected ChatClientResponse doAfterCall(ChatClientResponse response, CallAdvisorChain chain) {
		LoopState state = loopState.get();
		if (state == null) {
			return super.doAfterCall(response, chain);
		}

		// Update metrics
		TokenUsage tokens = extractTokenUsage(response);
		double cost = estimateCost(tokens.input(), tokens.output(), modelName);
		int outputSignature = computeOutputSignature(response);
		boolean hasToolCalls = hasToolCalls(response);
		String toolCallHash = toolCallHashTracker != null ? toolCallHashTracker.getAndResetTurnHash() : null;

		LoopState newState = state.completeTurn(tokens.input(), tokens.output(), cost, hasToolCalls, outputSignature,
				toolCallHash);
		loopState.set(newState);

		int completedTurn = state.currentTurn();
		log.debug("Turn {} completed: inputTokens={}, outputTokens={}, cost=${}, hasToolCalls={}", completedTurn + 1,
				tokens.input(), tokens.output(), String.format("%.4f", cost), hasToolCalls);

		// Check stuck detection (output-based and tool-call-based)
		checkStuckDetection(newState, response);
		checkToolCallStuckDetection(newState, response);

		// Notify turn completed (no termination)
		notifyTurnCompleted(state.runId(), completedTurn, null);

		return super.doAfterCall(response, chain);
	}

	// --- Termination checks ---

	private void checkAbortSignal(LoopState state) {
		if (abortSignal.get() || state.abortSignalled()) {
			log.info("Abort signal received for run {}", state.runId());
			notifyLoopCompleted(state.runId(), state, TerminationReason.EXTERNAL_SIGNAL);
			throw new AgentLoopTerminatedException(TerminationReason.EXTERNAL_SIGNAL, "Abort signal received", state);
		}
	}

	private void checkTimeout(LoopState state) {
		if (state.timeoutExceeded(config.timeout())) {
			log.info("Timeout exceeded for run {}: {} > {}", state.runId(), state.elapsed(), config.timeout());
			notifyLoopCompleted(state.runId(), state, TerminationReason.TIMEOUT);
			throw new AgentLoopTerminatedException(TerminationReason.TIMEOUT, "Timeout exceeded: " + config.timeout(),
					state);
		}
	}

	private void checkCostLimit(LoopState state) {
		if (config.costLimit() > 0 && state.costExceeded(config.costLimit())) {
			log.info("Cost limit exceeded for run {}: ${} > ${}", state.runId(),
					String.format("%.4f", state.estimatedCost()), String.format("%.4f", config.costLimit()));
			notifyLoopCompleted(state.runId(), state, TerminationReason.COST_LIMIT_EXCEEDED);
			throw new AgentLoopTerminatedException(TerminationReason.COST_LIMIT_EXCEEDED,
					String.format("Cost $%.4f exceeds limit $%.4f", state.estimatedCost(), config.costLimit()), state);
		}
	}

	private void checkMaxTurns(LoopState state) {
		if (state.maxTurnsReached(config.maxTurns())) {
			log.info("Max turns reached for run {}: {}/{}", state.runId(), state.currentTurn(), config.maxTurns());
			notifyLoopCompleted(state.runId(), state, TerminationReason.MAX_TURNS_REACHED);
			throw new AgentLoopTerminatedException(TerminationReason.MAX_TURNS_REACHED,
					"Max turns reached: " + config.maxTurns(), state);
		}
	}

	private void checkStuckDetection(LoopState state, ChatClientResponse response) {
		if (config.stuckThreshold() > 0 && state.isStuck(config.stuckThreshold())) {
			log.info("Agent stuck for run {}: same output {} times", state.runId(), config.stuckThreshold());
			notifyLoopCompleted(state.runId(), state, TerminationReason.STUCK_DETECTED);
			throw new AgentLoopTerminatedException(TerminationReason.STUCK_DETECTED,
					"Agent stuck: same output " + config.stuckThreshold() + " times", state, response);
		}
	}

	private static final int TOOL_CALL_STUCK_THRESHOLD = 5;

	private static final int TOOL_CALL_ALTERNATING_WINDOW = 10;

	private void checkToolCallStuckDetection(LoopState state, ChatClientResponse response) {
		if (toolCallHashTracker == null) {
			return;
		}
		if (state.isToolCallStuck(TOOL_CALL_STUCK_THRESHOLD)) {
			log.info("Agent stuck for run {}: same tool call repeated {} times", state.runId(),
					TOOL_CALL_STUCK_THRESHOLD);
			notifyLoopCompleted(state.runId(), state, TerminationReason.STUCK_DETECTED);
			throw new AgentLoopTerminatedException(TerminationReason.STUCK_DETECTED,
					"Agent stuck: same tool call repeated " + TOOL_CALL_STUCK_THRESHOLD + " times", state, response);
		}
		if (state.isAlternatingToolCalls(TOOL_CALL_ALTERNATING_WINDOW)) {
			log.info("Agent stuck for run {}: alternating tool call pattern detected in last {} turns", state.runId(),
					TOOL_CALL_ALTERNATING_WINDOW);
			notifyLoopCompleted(state.runId(), state, TerminationReason.STUCK_DETECTED);
			throw new AgentLoopTerminatedException(TerminationReason.STUCK_DETECTED,
					"Agent stuck: A-B-A-B tool call pattern in last " + TOOL_CALL_ALTERNATING_WINDOW + " turns", state,
					response);
		}
	}

	// --- Public API ---

	/**
	 * Signals the loop to abort at the next safe point.
	 * <p>
	 * The abort will be checked before the next LLM call, not during. This allows any
	 * in-progress operation to complete gracefully.
	 */
	public void abort() {
		abortSignal.set(true);
		log.debug("Abort signal set");
	}

	/**
	 * Checks if an abort has been signalled.
	 */
	public boolean isAbortSignalled() {
		return abortSignal.get();
	}

	/**
	 * Gets the current loop state (for the current thread).
	 * <p>
	 * Useful for debugging and status displays.
	 */
	public LoopState getCurrentState() {
		return loopState.get();
	}

	/**
	 * Gets the configuration.
	 */
	public AgentLoopConfig getConfig() {
		return config;
	}

	// --- Compaction ---

	/**
	 * Estimates token count from messages using chars/4 heuristic.
	 */
	static int estimateTokenCount(List<Message> messages) {
		int chars = 0;
		for (Message msg : messages) {
			String text = msg.getText();
			if (text != null) {
				chars += text.length();
			}
		}
		return chars / 4;
	}

	/**
	 * Returns true if the estimated token count exceeds the compaction threshold.
	 */
	boolean shouldCompact(ChatClientRequest request) {
		if (chatModel == null || compactionModelName == null) {
			return false;
		}
		List<Message> messages = request.prompt().getInstructions();
		int estimated = estimateTokenCount(messages);
		int threshold = (int) (contextLimit * compactionThreshold);
		return estimated > threshold;
	}

	/**
	 * Compresses old messages via the compaction model, preserving system messages and
	 * recent conversation.
	 */
	ChatClientRequest compactMessages(ChatClientRequest request) {
		List<Message> allMessages = request.prompt().getInstructions();

		// Separate system messages from conversation messages
		List<Message> systemMessages = new ArrayList<>();
		List<Message> conversationMessages = new ArrayList<>();
		for (Message msg : allMessages) {
			if (msg.getMessageType() == MessageType.SYSTEM) {
				systemMessages.add(msg);
			}
			else {
				conversationMessages.add(msg);
			}
		}

		// Need at least a few messages to compact
		if (conversationMessages.size() <= 3) {
			return request;
		}

		// Split: oldest 70% compressed, recent 30% kept
		int preserveCount = Math.max(1, (int) (conversationMessages.size() * PRESERVE_FRACTION));
		int compactCount = conversationMessages.size() - preserveCount;
		List<Message> toCompress = conversationMessages.subList(0, compactCount);
		List<Message> toKeep = conversationMessages.subList(compactCount, conversationMessages.size());

		// Build summary request for compaction model
		StringBuilder historyText = new StringBuilder();
		for (Message msg : toCompress) {
			historyText.append(msg.getMessageType().name()).append(": ").append(msg.getText()).append("\n\n");
		}

		try {
			log.info("Compacting {} messages ({} estimated tokens) via compaction model", toCompress.size(),
					estimateTokenCount(toCompress));

			String summary = ChatClient.builder(chatModel)
				.build()
				.prompt()
				.system(COMPACTION_PROMPT)
				.user(historyText.toString())
				.options(ChatOptions.builder().model(compactionModelName).build())
				.call()
				.content();

			// Build compacted message list: system + summary + recent
			List<Message> compactedMessages = new ArrayList<>(systemMessages);
			compactedMessages.add(new SystemMessage("[Compacted conversation summary]\n" + summary));
			compactedMessages.addAll(toKeep);

			// Update loop state
			LoopState state = loopState.get();
			if (state != null) {
				loopState.set(state.compacted());
			}

			log.info("Compaction complete: {} messages -> {} messages (compaction #{})", allMessages.size(),
					compactedMessages.size(), state != null ? state.compactionCount() + 1 : 1);

			Prompt compactedPrompt = new Prompt(compactedMessages, request.prompt().getOptions());
			return request.mutate().prompt(compactedPrompt).build();
		}
		catch (Exception ex) {
			log.warn("Compaction failed, continuing without compacting: {}", ex.getMessage());
			return request;
		}
	}

	// --- Helper methods ---

	private String extractUserMessage(ChatClientRequest request) {
		// Extract user message from request for logging/listeners
		if (request == null || request.prompt() == null) {
			return "";
		}
		var messages = request.prompt().getInstructions();
		if (messages == null || messages.isEmpty()) {
			return "";
		}
		// Get the last user message
		for (int i = messages.size() - 1; i >= 0; i--) {
			var msg = messages.get(i);
			if (msg.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
				return msg.getText();
			}
		}
		return "";
	}

	private TokenUsage extractTokenUsage(ChatClientResponse response) {
		if (response == null || response.chatResponse() == null) {
			return new TokenUsage(0, 0);
		}
		var metadata = response.chatResponse().getMetadata();
		if (metadata == null) {
			return new TokenUsage(0, 0);
		}
		var usage = metadata.getUsage();
		if (usage == null) {
			return new TokenUsage(0, 0);
		}
		long input = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
		long output = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
		return new TokenUsage(input, output);
	}

	static double estimateCost(long inputTokens, long outputTokens, String model) {
		double[] rates = DEFAULT_PRICING;
		if (model != null) {
			for (var entry : MODEL_PRICING.entrySet()) {
				if (model.startsWith(entry.getKey())) {
					rates = entry.getValue();
					break;
				}
			}
		}
		return (inputTokens * rates[0] + outputTokens * rates[1]) / 1_000_000.0;
	}

	record TokenUsage(long input, long output) {
	}

	private int computeOutputSignature(ChatClientResponse response) {
		if (response == null || response.chatResponse() == null) {
			return 0;
		}
		var result = response.chatResponse().getResult();
		if (result == null || result.getOutput() == null) {
			return 0;
		}
		String text = result.getOutput().getText();
		return text != null ? text.hashCode() : 0;
	}

	private boolean hasToolCalls(ChatClientResponse response) {
		if (response == null || response.chatResponse() == null) {
			return false;
		}
		// Use ChatResponse.hasToolCalls() which checks ALL generations,
		// not just the first one. Anthropic returns TEXT and TOOL_USE as
		// separate content blocks, creating multiple generations.
		return response.chatResponse().hasToolCalls();
	}

	// --- Listener notifications ---

	private void notifyLoopStarted(String runId, String message) {
		for (var listener : listeners) {
			try {
				listener.onLoopStarted(runId, message);
			}
			catch (Exception e) {
				log.warn("Listener error in onLoopStarted: {}", e.getMessage());
			}
		}
	}

	private void notifyTurnStarted(String runId, int turn) {
		for (var listener : listeners) {
			try {
				listener.onTurnStarted(runId, turn);
			}
			catch (Exception e) {
				log.warn("Listener error in onTurnStarted: {}", e.getMessage());
			}
		}
	}

	private void notifyTurnCompleted(String runId, int turn, TerminationReason reason) {
		for (var listener : listeners) {
			try {
				listener.onTurnCompleted(runId, turn, reason);
			}
			catch (Exception e) {
				log.warn("Listener error in onTurnCompleted: {}", e.getMessage());
			}
		}
	}

	private void notifyLoopCompleted(String runId, LoopState state, TerminationReason reason) {
		for (var listener : listeners) {
			try {
				listener.onLoopCompleted(runId, state, reason);
			}
			catch (Exception e) {
				log.warn("Listener error in onLoopCompleted: {}", e.getMessage());
			}
		}
	}

	private void notifyLoopFailed(String runId, LoopState state, Throwable error) {
		for (var listener : listeners) {
			try {
				listener.onLoopFailed(runId, state, error);
			}
			catch (Exception e) {
				log.warn("Listener error in onLoopFailed: {}", e.getMessage());
			}
		}
	}

	// --- Builder ---

	/**
	 * Creates a new Builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link AgentLoopAdvisor}.
	 */
	public static class Builder extends ToolCallAdvisor.Builder<Builder> {

		private ToolCallingManager toolCallingManager;

		private int advisorOrder = 0;

		private int maxTurns = AgentLoopConfig.DEFAULT_MAX_TURNS;

		private Duration timeout = AgentLoopConfig.DEFAULT_TIMEOUT;

		private double costLimit = AgentLoopConfig.DEFAULT_COST_LIMIT;

		private int stuckThreshold = AgentLoopConfig.DEFAULT_STUCK_THRESHOLD;

		private Path workingDirectory = Path.of(".");

		private String modelName;

		private @Nullable ChatModel chatModel;

		private @Nullable String compactionModelName;

		private int contextLimit = DEFAULT_CONTEXT_LIMIT;

		private double compactionThreshold = DEFAULT_COMPACTION_THRESHOLD;

		private List<AgentLoopListener> listeners = new ArrayList<>();

		private @Nullable ToolCallHashTracker toolCallHashTracker;

		protected Builder() {
		}

		@Override
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return super.toolCallingManager(toolCallingManager);
		}

		@Override
		public Builder advisorOrder(int order) {
			this.advisorOrder = order;
			return super.advisorOrder(order);
		}

		/**
		 * Sets the maximum number of turns allowed.
		 * @param maxTurns maximum turns, must be at least 1
		 * @return this builder
		 */
		public Builder maxTurns(int maxTurns) {
			this.maxTurns = maxTurns;
			return this;
		}

		/**
		 * Sets the timeout duration.
		 * @param timeout maximum duration for the loop
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Sets the cost limit in dollars.
		 * @param costLimit maximum cost, 0 to disable
		 * @return this builder
		 */
		public Builder costLimit(double costLimit) {
			this.costLimit = costLimit;
			return this;
		}

		/**
		 * Sets the stuck detection threshold.
		 * @param threshold consecutive identical outputs to detect stuck, 0 to disable
		 * @return this builder
		 */
		public Builder stuckThreshold(int threshold) {
			this.stuckThreshold = threshold;
			return this;
		}

		/**
		 * Sets the working directory.
		 * @param workingDirectory the working directory
		 * @return this builder
		 */
		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		/**
		 * Sets the model name for cost estimation.
		 * @param modelName model identifier (e.g. "claude-haiku-4-5-20251001")
		 * @return this builder
		 */
		public Builder modelName(String modelName) {
			this.modelName = modelName;
			return this;
		}

		/**
		 * Adds a listener for loop events.
		 * @param listener the listener to add
		 * @return this builder
		 */
		public Builder listener(AgentLoopListener listener) {
			this.listeners.add(listener);
			return this;
		}

		/**
		 * Sets the primary chat model used for compaction requests.
		 * @param chatModel the primary chat model (same credentials, different model name
		 * via options override)
		 * @return this builder
		 */
		public Builder chatModel(@Nullable ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		/**
		 * Sets the model name to use for compaction. If null (default), compaction is
		 * disabled.
		 * @param compactionModelName cheap model name for summarizing old messages
		 * @return this builder
		 */
		public Builder compactionModelName(@Nullable String compactionModelName) {
			this.compactionModelName = compactionModelName;
			return this;
		}

		/**
		 * Sets the context token limit for compaction threshold calculation.
		 * @param contextLimit model context window size in tokens (default 200,000)
		 * @return this builder
		 */
		public Builder contextLimit(int contextLimit) {
			this.contextLimit = contextLimit;
			return this;
		}

		/**
		 * Sets the fraction of context limit that triggers compaction.
		 * @param compactionThreshold fraction (0.0-1.0), default 0.5
		 * @return this builder
		 */
		public Builder compactionThreshold(double compactionThreshold) {
			this.compactionThreshold = compactionThreshold;
			return this;
		}

		/**
		 * Applies a preset configuration.
		 * @param config the configuration to apply
		 * @return this builder
		 */
		public Builder config(AgentLoopConfig config) {
			this.maxTurns = config.maxTurns();
			this.timeout = config.timeout();
			this.costLimit = config.costLimit();
			this.stuckThreshold = config.stuckThreshold();
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		public Builder toolCallHashTracker(ToolCallHashTracker tracker) {
			this.toolCallHashTracker = tracker;
			return this;
		}

		public AgentLoopAdvisor build() {
			return new AgentLoopAdvisor(this);
		}

	}

}
