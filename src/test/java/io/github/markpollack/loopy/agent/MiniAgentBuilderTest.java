package io.github.markpollack.loopy.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MiniAgentBuilderTest {

	@Test
	void builderRequiresConfig() {
		assertThatThrownBy(() -> MiniAgent.builder().model(mock(ChatModel.class)).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("config is required");
	}

	@Test
	void builderRequiresModel() {
		var config = MiniAgentConfig.builder().workingDirectory(Path.of(".")).build();
		assertThatThrownBy(() -> MiniAgent.builder().config(config).build()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("model is required");
	}

	@Test
	void builderSucceedsWithRequiredFields() {
		var config = MiniAgentConfig.builder().workingDirectory(Path.of(".")).build();
		var model = mock(ChatModel.class);
		var agent = MiniAgent.builder().config(config).model(model).build();
		assertThat(agent).isNotNull();
		assertThat(agent.isInteractive()).isFalse();
		assertThat(agent.hasSessionMemory()).isFalse();
	}

	@Test
	void builderWithSessionMemory() {
		var config = MiniAgentConfig.builder().workingDirectory(Path.of(".")).build();
		var model = mock(ChatModel.class);
		var agent = MiniAgent.builder().config(config).model(model).sessionMemory().build();
		assertThat(agent.hasSessionMemory()).isTrue();
	}

	@Test
	void builderWithSkillsDirectory(@TempDir Path tempDir) throws IOException {
		// Create .claude/skills/ with a SKILL.md file
		Path skillsDir = tempDir.resolve(".claude/skills/test-skill");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
				---
				name: test-skill
				description: A test skill for unit testing
				---

				# Test Skill

				This is test skill content.
				""");

		var config = MiniAgentConfig.builder().workingDirectory(tempDir).build();
		var model = mock(ChatModel.class);
		// Should build successfully with skills discovered
		var agent = MiniAgent.builder().config(config).model(model).build();
		assertThat(agent).isNotNull();
	}

	@Test
	void builderWithoutSkillsDirectoryStillWorks() {
		// Working directory has no .claude/skills/ — agent should build fine without
		// skills
		var config = MiniAgentConfig.builder().workingDirectory(Path.of("/tmp/nonexistent-dir")).build();
		var model = mock(ChatModel.class);
		var agent = MiniAgent.builder().config(config).model(model).build();
		assertThat(agent).isNotNull();
	}

}
