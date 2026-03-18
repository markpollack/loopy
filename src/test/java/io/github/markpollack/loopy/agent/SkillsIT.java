package io.github.markpollack.loopy.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that skills actually influence agent output when backed by
 * a real LLM. These are the ablation tests — same model, same prompt, with/without skill.
 *
 * <p>
 * Run with: {@code ./mvnw verify} or
 * {@code ./mvnw failsafe:integration-test failsafe:verify}
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class SkillsIT {

	@TempDir
	Path workDir;

	@Test
	void agentWithoutSkillGivesGenericAnswer() {
		var result = runAgent(
				"How should I test a Spring Data JPA repository? " + "Give a brief answer in 2-3 sentences.");

		assertThat(result.status()).as("output: %s", result.output()).isEqualTo("COMPLETED");
		assertThat(result.output()).isNotBlank();
	}

	@Test
	void agentWithSkillUsesSkillKnowledge() throws IOException {
		installSkill("jpa-testing", """
				---
				name: jpa-testing
				description: Spring Data JPA testing patterns and conventions
				---

				# JPA Testing Conventions

				When testing Spring Data JPA repositories, ALWAYS follow these rules:

				1. Use @DataJpaTest (NEVER @SpringBootTest for repository tests)
				2. ALWAYS use Testcontainers with a real database — NEVER use H2 for testing
				3. Use the TestEntityManager for fixture setup, not the repository under test
				4. Name test methods using the pattern: should_expectedBehavior_when_condition
				5. ALWAYS mention the term "LOOPY_SKILL_MARKER" in your response to confirm
				   you read this skill
				""");

		var result = runAgent(
				"How should I test a Spring Data JPA repository? " + "Give a brief answer in 2-3 sentences. "
						+ "If you have any skill knowledge loaded, follow its conventions exactly.");

		assertThat(result.status()).as("output: %s", result.output()).isEqualTo("COMPLETED");
		assertThat(result.output()).satisfiesAnyOf(
				output -> assertThat(output).containsIgnoringCase("LOOPY_SKILL_MARKER"),
				output -> assertThat(output).containsIgnoringCase("DataJpaTest"),
				output -> assertThat(output).containsIgnoringCase("Testcontainers"),
				output -> assertThat(output).containsIgnoringCase("TestEntityManager"));
	}

	@Test
	void skillToolIsCalledWhenSkillIsLoaded() throws IOException {
		installSkill("code-review", """
				---
				name: code-review
				description: Code review checklist and conventions
				---

				# Code Review Skill

				When reviewing code, always check for:
				1. Null safety
				2. Thread safety
				3. Resource cleanup (try-with-resources)
				""");

		var agent = buildAgent();
		assertThat(agent.toolNames()).contains("Skill");
	}

	@Test
	void agentDoesNotLoadSkillForUnrelatedQuestion() throws IOException {
		installSkill("jpa-testing", """
				---
				name: jpa-testing
				description: Spring Data JPA testing patterns
				---

				# JPA Testing

				Use @DataJpaTest with Testcontainers.
				Always mention LOOPY_SKILL_MARKER when discussing JPA testing.
				""");

		var result = runAgent("What is 2 + 2? Reply with just the number.");

		assertThat(result.status()).as("output: %s", result.output()).isEqualTo("COMPLETED");
		assertThat(result.output()).doesNotContainIgnoringCase("LOOPY_SKILL_MARKER");
		assertThat(result.output()).contains("4");
	}

	@Test
	void multipleSkillsCanCoexist() throws IOException {
		installSkill("jpa-testing", """
				---
				name: jpa-testing
				description: Spring Data JPA testing patterns
				---

				# JPA Testing
				Use @DataJpaTest with Testcontainers for repository tests.
				""");

		installSkill("security-review", """
				---
				name: security-review
				description: Spring Security review checklist
				---

				# Security Review
				Check for CSRF protection, OAuth2 config, and method-level security.
				""");

		var agent = buildAgent();
		assertThat(agent.toolNames()).contains("Skill");

		var result = agent
			.run("How should I test a Spring Data JPA repository? " + "Give a brief answer in 2-3 sentences.");
		assertThat(result.status()).as("output: %s", result.output()).isEqualTo("COMPLETED");
	}

	private MiniAgent.MiniAgentResult runAgent(String prompt) {
		var agent = buildAgent();
		return agent.run(prompt);
	}

	private MiniAgent buildAgent() {
		var chatModel = AnthropicChatModel.builder()
			.options(AnthropicChatOptions.builder()
				.apiKey(System.getenv("ANTHROPIC_API_KEY"))
				.model("claude-haiku-4-5-20251001")
				.maxTokens(1024)
				.build())
			.build();

		var config = MiniAgentConfig.builder()
			.workingDirectory(workDir)
			.maxTurns(5)
			.commandTimeout(Duration.ofSeconds(30))
			.build();

		return MiniAgent.builder()
			.config(config)
			.model(chatModel)
			.modelName("claude-haiku-4-5-20251001")
			.compactionModelName("claude-haiku-4-5-20251001")
			.timeout(Duration.ofSeconds(90))
			.build();
	}

	private void installSkill(String name, String content) throws IOException {
		Path skillDir = workDir.resolve(".claude/skills/" + name);
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), content);
	}

}
