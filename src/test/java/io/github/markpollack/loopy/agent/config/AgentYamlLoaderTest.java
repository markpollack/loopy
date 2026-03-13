package io.github.markpollack.loopy.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentYamlLoaderTest {

	@Test
	void defaultsWhenNoFile(@TempDir Path dir) {
		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("dev", "boot");
		assertThat(yaml.maxTurnsOverride()).isNull();
		assertThat(yaml.modelOverride()).isNull();
	}

	@Test
	void loadsProfilesFromProjectFile(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), """
				tools:
				  profiles:
				    - headless
				    - boot
				""");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("headless", "boot");
	}

	@Test
	void loadsAgentSectionFromProjectFile(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), """
				agent:
				  name: pr-review-agent
				  description: Reviews pull requests
				  version: 1.0.0
				tools:
				  profiles:
				    - dev
				""");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.agent()).isNotNull();
		assertThat(yaml.agent().name()).isEqualTo("pr-review-agent");
		assertThat(yaml.agent().description()).isEqualTo("Reviews pull requests");
		assertThat(yaml.agent().version()).isEqualTo("1.0.0");
	}

	@Test
	void loadsRuntimeOverridesFromProjectFile(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), """
				runtime:
				  max-turns: 30
				  model: claude-haiku-4-5-20251001
				  cost-limit-dollars: 2.50
				""");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.maxTurnsOverride()).isEqualTo(30);
		assertThat(yaml.modelOverride()).isEqualTo("claude-haiku-4-5-20251001");
		// defaults still apply for profiles when tools section absent
		assertThat(yaml.activeProfiles()).containsExactly("dev", "boot");
	}

	@Test
	void returnsDefaultsForEmptyFile(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), "");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("dev", "boot");
	}

	@Test
	void returnsDefaultsForMalformedFile(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), ": invalid: yaml: {{{");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("dev", "boot");
	}

	@Test
	void singleDevProfileOnly(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), """
				tools:
				  profiles:
				    - dev
				""");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("dev");
		assertThat(yaml.activeProfiles()).doesNotContain("boot");
	}

	@Test
	void readonlyProfileExcludesBoot(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("agent.yaml"), """
				tools:
				  profiles:
				    - readonly
				""");

		AgentYaml yaml = AgentYamlLoader.load(dir);

		assertThat(yaml.activeProfiles()).containsExactly("readonly");
		assertThat(yaml.activeProfiles()).doesNotContainAnyElementsOf(List.of("dev", "boot"));
	}

}
