package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the full NL → MiniAgent → BootNewTool → filesystem path.
 *
 * <p>
 * These tests require a real {@code ANTHROPIC_API_KEY} and make actual API calls. They
 * are excluded from {@code ./mvnw test} (surefire) and run only under
 * {@code ./mvnw verify} (failsafe) when the environment variable is present.
 * </p>
 *
 * <p>
 * The pattern mirrors terminal-bench: a natural language prompt is fed to the agent, and
 * success is evaluated against deterministic filesystem assertions rather than LLM output
 * text.
 * </p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class BootNewAgentIT {

	@TempDir
	Path tempDir;

	// --- bootstrap / easy ---

	@Test
	void scaffoldsMinimalProjectFromNaturalLanguage() throws Exception {
		var result = agent().run(
				"Create a Spring Boot project named 'hello-world' with groupId com.example. Use the spring-boot-minimal template.");

		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(tempDir.resolve("hello-world/pom.xml")).isRegularFile();
		assertThat(tempDir.resolve("hello-world/src/main/java/com/example/helloworld/Application.java"))
			.isRegularFile();
		assertThat(Files.readString(tempDir.resolve("hello-world/pom.xml"))).isNotNull()
			.contains("<artifactId>hello-world</artifactId>")
			.contains("<groupId>com.example</groupId>");
	}

	// --- basic / medium ---

	@Test
	void scaffoldsRestProjectFromNaturalLanguage() throws Exception {
		var result = agent().run(
				"Scaffold a new Spring Boot REST API project named 'orders-api' for Maven group com.acme. Use the spring-boot-rest template.");

		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(tempDir.resolve("orders-api/pom.xml")).isRegularFile();
		assertThat(tempDir.resolve("orders-api/src/main/java/com/acme/ordersapi/greeting/GreetingController.java"))
			.isRegularFile();
	}

	// --- intermediate ---

	@Test
	void scaffoldsJpaProjectFromNaturalLanguage() throws Exception {
		var result = agent().run(
				"Generate a Spring Boot JPA project. Name it 'catalog-service', group com.corp. Use the spring-boot-jpa template.");

		assertThat(result.status()).isEqualTo("COMPLETED");
		assertThat(tempDir.resolve("catalog-service/pom.xml")).isRegularFile();
		String pom = Files.readString(tempDir.resolve("catalog-service/pom.xml"));
		assertThat(pom).contains("spring-boot-starter-data-jpa");
	}

	// --- helpers ---

	private MiniAgent agent() {
		var chatModel = haiku();
		MiniAgentConfig config = MiniAgentConfig.builder().workingDirectory(tempDir).maxTurns(5).build();
		return MiniAgent.builder()
			.config(config)
			.model(chatModel)
			.additionalTools(new BootNewTool(tempDir, null))
			.disabledTools(Set.of("Task", "TaskOutput"))
			.build();
	}

	private static AnthropicChatModel haiku() {
		return AnthropicChatModel.builder()
			.options(AnthropicChatOptions.builder()
				.apiKey(System.getenv("ANTHROPIC_API_KEY"))
				.model("claude-haiku-4-5-20251001")
				.maxTokens(2048)
				.build())
			.build();
	}

}
