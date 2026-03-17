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
 * Verifies Stage 8c: SkillsJars classpath scanning.
 * <p>
 * A SKILL.md at {@code META-INF/skills/test-org/test-repo/classpath-skill/SKILL.md} in
 * the test classpath (src/test/resources) simulates a SkillsJar dependency declared in a
 * project's pom.xml. MiniAgent scans this prefix at startup — no disk install step
 * required.
 */
class ClasspathSkillsScanningTest {

	@Test
	void agentRegistersSkillToolFromClasspathWithNoFilesystemSkills(@TempDir Path tempDir) {
		// No .claude/skills/ directory — Skill tool comes from classpath only
		var agent = buildAgent(tempDir);

		assertThat(agent.toolNames()).contains("Skill");
	}

	@Test
	void classpathSkillsAndFilesystemSkillsMerge(@TempDir Path tempDir) throws IOException {
		// Both a disk skill and a classpath skill are present
		Path skillDir = tempDir.resolve(".claude/skills/spring-boot");
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), """
				---
				name: spring-boot
				description: Spring Boot conventions
				---
				Use Spring Boot conventions.
				""");

		var agent = buildAgent(tempDir);

		// Skill tool registered (both sources contribute, but tool name is always
		// "Skill")
		assertThat(agent.toolNames()).contains("Skill");
	}

	@Test
	void classpathSkillsCoexistWithOtherTools(@TempDir Path tempDir) {
		var agent = buildAgent(tempDir);

		assertThat(agent.toolNames()).contains("Skill", "bash", "Glob", "Grep", "Submit");
	}

	@Test
	void classpathSkillsCanBeDisabledExplicitly(@TempDir Path tempDir) {
		var config = MiniAgentConfig.builder().workingDirectory(tempDir).build();
		var agent = MiniAgent.builder()
			.config(config)
			.model(mock(ChatModel.class))
			.disabledTools(java.util.Set.of("Skills"))
			.build();

		assertThat(agent.toolNames()).doesNotContain("Skill");
	}

	private MiniAgent buildAgent(Path workDir) {
		var config = MiniAgentConfig.builder().workingDirectory(workDir).build();
		return MiniAgent.builder().config(config).model(mock(ChatModel.class)).build();
	}

}
