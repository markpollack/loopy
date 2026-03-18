package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.agent.MiniAgent;
import io.github.markpollack.loopy.agent.MiniAgentConfig;

import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for the full NL → MiniAgent → BootModifyTool → filesystem path.
 *
 * <p>
 * Each test starts from a deterministically scaffolded minimal project (no LLM), then
 * drives a natural language modification request through MiniAgent and asserts on the
 * resulting {@code pom.xml} or filesystem state.
 * </p>
 *
 * <p>
 * Requires {@code ANTHROPIC_API_KEY} and runs under {@code ./mvnw verify} (failsafe).
 * </p>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class BootModifyAgentIT {

	@TempDir
	Path tempDir;

	/**
	 * Scaffolded without LLM so every modify test starts from the same clean baseline.
	 */
	@BeforeEach
	void scaffoldBaseProject() {
		BootNewTool scaffoldTool = new BootNewTool(tempDir, null);
		scaffoldTool.bootNew("test-app", "com.example", "spring-boot-minimal", "21");
	}

	private Path projectDir() {
		return tempDir.resolve("test-app");
	}

	// --- bootstrap / easy ---

	@Test
	void setsJavaVersionViaAgent() throws Exception {
		agent().run("Set the Java version to 17");

		String pom = Files.readString(projectDir().resolve("pom.xml"));
		assertThat(pom).contains("<java.version>17</java.version>").doesNotContain("<java.version>21</java.version>");
	}

	// --- basic / medium ---

	@Test
	void addsActuatorViaAgent() throws Exception {
		agent().run("Add Spring Boot Actuator to the project");

		String pom = Files.readString(projectDir().resolve("pom.xml"));
		assertThat(pom).contains("spring-boot-starter-actuator");
	}

	@Test
	void addsSecurityViaAgent() throws Exception {
		agent().run("Add Spring Security");

		String pom = Files.readString(projectDir().resolve("pom.xml"));
		assertThat(pom).contains("spring-boot-starter-security");
	}

	// --- intermediate ---

	@Test
	void addsBasicCiViaAgent() throws Exception {
		agent().run("Add a basic GitHub Actions CI workflow for Maven builds");

		Path workflow = projectDir().resolve(".github/workflows/build.yml");
		assertThat(workflow).isRegularFile();
		assertThat(Files.readString(workflow)).contains("mvn");
	}

	// --- helpers ---

	private MiniAgent agent() {
		var chatModel = haiku();
		Path projectDir = projectDir();
		MiniAgentConfig config = MiniAgentConfig.builder().workingDirectory(projectDir).maxTurns(5).build();
		return MiniAgent.builder()
			.config(config)
			.model(chatModel)
			.additionalTools(new BootModifyTool(projectDir))
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
