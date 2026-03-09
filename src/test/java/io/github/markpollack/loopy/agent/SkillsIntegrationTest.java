package io.github.markpollack.loopy.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for skills discovery in MiniAgent.
 * <p>
 * Verifies that SkillsTool is registered when skill directories contain SKILL.md files,
 * and absent when no skills exist.
 */
class SkillsIntegrationTest {

	@Test
	void agentRegistersSkillToolWhenProjectSkillsExist(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, ".claude/skills/spring-boot", "spring-boot", "Spring Boot conventions and best practices");

		var agent = buildAgent(tempDir);
		assertThat(agent.toolNames()).contains("Skill");
	}

	@Test
	void agentRegistersSkillToolWithMultipleSkills(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, ".claude/skills/spring-boot", "spring-boot", "Spring Boot conventions and best practices");
		createSkill(tempDir, ".claude/skills/jpa-testing", "jpa-testing", "JPA test patterns with Testcontainers");

		var agent = buildAgent(tempDir);
		assertThat(agent.toolNames()).contains("Skill");
	}

	@Test
	void agentOmitsSkillToolWhenNoSkillsExist(@TempDir Path tempDir) {
		// No .claude/skills/ directory — SkillsTool should not be registered
		var agent = buildAgent(tempDir);
		assertThat(agent.toolNames()).doesNotContain("Skill");
	}

	@Test
	void agentOmitsSkillToolWhenDirectoryExistsButEmpty(@TempDir Path tempDir) throws IOException {
		// Directory exists but has no SKILL.md files
		Files.createDirectories(tempDir.resolve(".claude/skills"));

		var agent = buildAgent(tempDir);
		assertThat(agent.toolNames()).doesNotContain("Skill");
	}

	@Test
	void agentCanDisableSkillTool(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, ".claude/skills/spring-boot", "spring-boot", "Spring Boot conventions and best practices");

		var config = MiniAgentConfig.builder().workingDirectory(tempDir).build();
		var agent = MiniAgent.builder()
			.config(config)
			.model(mock(ChatModel.class))
			.disabledTools(java.util.Set.of("Skills"))
			.build();

		assertThat(agent.toolNames()).doesNotContain("Skill");
	}

	@Test
	void skillToolCoexistsWithOtherTools(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, ".claude/skills/pdf", "pdf", "Extract text and tables from PDF files");

		var agent = buildAgent(tempDir);

		// Skill tool registered alongside standard tools
		assertThat(agent.toolNames()).contains("Skill", "bash", "Glob", "Grep", "Submit");
	}

	@Test
	void nestedSkillDirectoriesDiscovered(@TempDir Path tempDir) throws IOException {
		// Skills can be nested arbitrarily deep
		createSkill(tempDir, ".claude/skills/community/sivalabs/spring-boot", "spring-boot", "Spring Boot conventions");

		var agent = buildAgent(tempDir);
		assertThat(agent.toolNames()).contains("Skill");
	}

	private MiniAgent buildAgent(Path workDir) {
		var config = MiniAgentConfig.builder().workingDirectory(workDir).build();
		return MiniAgent.builder().config(config).model(mock(ChatModel.class)).build();
	}

	private void createSkill(Path root, String relativePath, String name, String description) throws IOException {
		Path skillDir = root.resolve(relativePath);
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), """
				---
				name: %s
				description: %s
				---

				# %s

				Detailed instructions for the %s skill.
				Use this knowledge when working on related tasks.
				""".formatted(name, description, name, name));
	}

}
