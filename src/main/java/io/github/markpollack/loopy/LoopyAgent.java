package io.github.markpollack.loopy;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgent.MiniAgentResult;
import io.github.markpollack.loopy.agent.MiniAgentConfig;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * Stable programmatic API for running Loopy headless from other Java projects.
 * <p>
 * Constructs a {@link MiniAgent} internally with the specified model and configuration.
 * Consumers see only this class and {@link LoopyResult} — all Loopy internals stay
 * internal.
 * <p>
 * Session memory is enabled by default: multiple {@link #run(String)} calls on the same
 * instance preserve conversation context (plan+act pattern is just two run() calls).
 * <p>
 * Example: <pre>{@code
 * LoopyAgent agent = LoopyAgent.builder()
 *     .model("claude-haiku-4-5-20251001")
 *     .workingDirectory(workspace)
 *     .systemPrompt(prompt)
 *     .maxTurns(80)
 *     .build();
 *
 * LoopyResult planResult = agent.run(planPrompt);
 * LoopyResult actResult = agent.run(actPrompt);  // sees plan context
 * }</pre>
 */
public class LoopyAgent {

	private final MiniAgent delegate;

	private LoopyAgent(MiniAgent delegate) {
		this.delegate = delegate;
	}

	/**
	 * Run a task. With session memory enabled (default), context is preserved across
	 * calls.
	 */
	public LoopyResult run(String task) {
		MiniAgentResult result = delegate.run(task);
		return new LoopyResult(result.status(), result.output(), result.turnsCompleted(), result.toolCallsExecuted(),
				result.totalTokens(), result.inputTokens(), result.outputTokens(), result.estimatedCost());
	}

	/**
	 * Clear session memory, start fresh.
	 */
	public void clearSession() {
		delegate.clearSession();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Result from a single agent run.
	 */
	public record LoopyResult(String status, String output, int turnsCompleted, int toolCallsExecuted, long totalTokens,
			long inputTokens, long outputTokens, double estimatedCost) {
	}

	public static class Builder {

		private String modelId;

		private Path workingDirectory;

		private String systemPrompt;

		private int maxTurns = 80;

		private boolean sessionMemory = true;

		private Duration commandTimeout = Duration.ofSeconds(120);

		private Duration timeout;

		private String baseUrl;

		private String apiKey;

		private Set<String> disabledTools = Set.of();

		private boolean compactionEnabled = true;

		private int contextLimit = 200_000;

		private double compactionThreshold = 0.5;

		private double costLimit = 5.0;

		public Builder model(String modelId) {
			this.modelId = modelId;
			return this;
		}

		public Builder workingDirectory(Path dir) {
			this.workingDirectory = dir;
			return this;
		}

		public Builder systemPrompt(String prompt) {
			this.systemPrompt = prompt;
			return this;
		}

		public Builder maxTurns(int turns) {
			this.maxTurns = turns;
			return this;
		}

		public Builder sessionMemory(boolean enabled) {
			this.sessionMemory = enabled;
			return this;
		}

		public Builder commandTimeout(Duration timeout) {
			this.commandTimeout = timeout;
			return this;
		}

		/**
		 * Override the Anthropic API base URL (for compatible endpoints like vLLM, LM
		 * Studio).
		 */
		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		/**
		 * Override the API key (defaults to ANTHROPIC_API_KEY env var).
		 */
		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		/**
		 * Override the agent loop timeout (defaults to 10 minutes).
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Enable or disable context compaction for long sessions (default true). When
		 * enabled, old messages are summarized via Haiku when context exceeds 50% of the
		 * model limit.
		 */
		public Builder compactionEnabled(boolean enabled) {
			this.compactionEnabled = enabled;
			return this;
		}

		/**
		 * Set the context token limit for compaction (default 200,000). Compaction
		 * triggers when estimated tokens exceed contextLimit * compactionThreshold.
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
		 * Set the maximum cost in dollars before the agent stops (default $5.00).
		 */
		public Builder costLimit(double costLimit) {
			this.costLimit = costLimit;
			return this;
		}

		/**
		 * Disable specific tools by name (e.g., "Task", "TodoWrite").
		 */
		public Builder disabledTools(Set<String> toolNames) {
			this.disabledTools = toolNames;
			return this;
		}

		public LoopyAgent build() {
			if (workingDirectory == null) {
				throw new IllegalStateException("workingDirectory is required");
			}

			// Resolve API key: explicit > env var
			String resolvedApiKey = this.apiKey != null ? this.apiKey : System.getenv("ANTHROPIC_API_KEY");
			if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
				throw new IllegalStateException("API key not set: use .apiKey() or ANTHROPIC_API_KEY env var");
			}

			String model = modelId != null ? modelId : "claude-sonnet-4-6";
			var apiBuilder = AnthropicApi.builder().apiKey(resolvedApiKey);
			if (baseUrl != null) {
				apiBuilder.baseUrl(baseUrl);
				// Custom endpoints (LM Studio, vLLM) typically only support HTTP/1.1.
				// Java 21's HttpClient defaults to HTTP/2 which hangs on HTTP/1.1
				// servers.
				var httpClient = java.net.http.HttpClient.newBuilder()
					.version(java.net.http.HttpClient.Version.HTTP_1_1)
					.build();
				var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
				apiBuilder.restClientBuilder(
						org.springframework.web.client.RestClient.builder().requestFactory(requestFactory));
			}
			var anthropicApi = apiBuilder.build();
			var chatModel = AnthropicChatModel.builder()
				.anthropicApi(anthropicApi)
				.defaultOptions(AnthropicChatOptions.builder().model(model).maxTokens(16384).build())
				.build();

			// Build MiniAgent config
			var configBuilder = MiniAgentConfig.builder()
				.workingDirectory(workingDirectory)
				.maxTurns(maxTurns)
				.costLimit(costLimit)
				.commandTimeout(commandTimeout);

			if (systemPrompt != null) {
				configBuilder.systemPrompt(systemPrompt);
			}

			var config = configBuilder.build();

			// Build MiniAgent
			var agentBuilder = MiniAgent.builder()
				.config(config)
				.model(chatModel)
				.modelName(model)
				.disabledTools(disabledTools);

			if (timeout != null) {
				agentBuilder.timeout(timeout);
			}

			if (sessionMemory) {
				agentBuilder.sessionMemory();
			}

			agentBuilder.contextLimit(contextLimit);
			agentBuilder.compactionThreshold(compactionThreshold);

			// Create compaction model (Haiku) for context compaction
			if (compactionEnabled && baseUrl == null) {
				var compactionApi = AnthropicApi.builder().apiKey(resolvedApiKey).build();
				var compactionChatModel = AnthropicChatModel.builder()
					.anthropicApi(compactionApi)
					.defaultOptions(
							AnthropicChatOptions.builder().model("claude-haiku-4-5-20251001").maxTokens(4096).build())
					.build();
				agentBuilder.compactionModel(compactionChatModel);
			}

			return new LoopyAgent(agentBuilder.build());
		}

	}

}
