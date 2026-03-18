package io.github.markpollack.loopy.agent;

import java.nio.file.Path;
import java.time.Duration;

import com.google.genai.Client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying each provider can run a simple agent task. Guarded by
 * environment variables so they only run when API keys are present.
 *
 * <p>
 * Run with: {@code ./mvnw verify} or
 * {@code ./mvnw failsafe:integration-test failsafe:verify}
 */
class MultiProviderIT {

	@TempDir
	Path workDir;

	@Test
	@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
	void anthropicSimpleTask() {
		var chatModel = AnthropicChatModel.builder()
			.options(AnthropicChatOptions.builder()
				.apiKey(System.getenv("ANTHROPIC_API_KEY"))
				.model("claude-haiku-4-5-20251001")
				.maxTokens(1024)
				.build())
			.build();

		var result = runSimpleTask(chatModel, "claude-haiku-4-5-20251001");

		assertResult(result);
		assertThat(result.estimatedCost()).isGreaterThan(0.0);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
	void openaiSimpleTask() {
		var api = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();
		var chatModel = OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
			.build();

		var result = runSimpleTask(chatModel, "gpt-4o-mini");

		assertResult(result);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
	void googleGenaiSimpleTask() throws Exception {
		var client = Client.builder().apiKey(System.getenv("GOOGLE_API_KEY")).build();
		var chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(client)
			.defaultOptions(GoogleGenAiChatOptions.builder().model("gemini-2.5-flash-lite").build())
			.build();

		var result = runSimpleTask(chatModel, "gemini-2.5-flash-lite");

		assertResult(result);
	}

	private MiniAgent.MiniAgentResult runSimpleTask(ChatModel chatModel, String compactionModelName) {
		var config = MiniAgentConfig.builder()
			.workingDirectory(workDir)
			.maxTurns(3)
			.commandTimeout(Duration.ofSeconds(30))
			.build();

		var agent = MiniAgent.builder()
			.config(config)
			.model(chatModel)
			.modelName(compactionModelName)
			.compactionModelName(compactionModelName)
			.timeout(Duration.ofSeconds(60))
			.build();

		return agent.run("Reply with exactly: hello world");
	}

	private void assertResult(MiniAgent.MiniAgentResult result) {
		assertThat(result.status()).as("output: %s", result.output()).isEqualTo("COMPLETED");
		assertThat(result.output()).containsIgnoringCase("hello");
	}

}
