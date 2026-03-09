package io.github.markpollack.loopy.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsCommandTest {

	private final SkillsCommand command = new SkillsCommand();

	@Test
	void listNoSkills(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("list", ctx);
		assertThat(result).contains("No skills found");
	}

	@Test
	void listDiscoveredSkills(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, "test-skill", "A test skill");

		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("list", ctx);
		assertThat(result).contains("test-skill").contains("A test skill").contains("project");
	}

	@Test
	void infoShowsContent(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, "my-skill", "Does something useful");

		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("info my-skill", ctx);
		assertThat(result).contains("my-skill").contains("Does something useful").contains("Skill body content");
	}

	@Test
	void infoNotFound(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("info nonexistent", ctx);
		assertThat(result).contains("Skill not found");
	}

	@Test
	void defaultSubcommandIsList(@TempDir Path tempDir) throws IOException {
		createSkill(tempDir, "default-test", "Testing default");

		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("", ctx);
		assertThat(result).contains("default-test");
	}

	@Test
	void unknownSubcommand(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("bogus", ctx);
		assertThat(result).contains("Unknown subcommand");
	}

	@Test
	void searchFindsResults(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search spring", ctx);
		assertThat(result).contains("spring-boot").contains("sivalabs");
	}

	@Test
	void searchNoResults(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search xyznonexistent", ctx);
		assertThat(result).contains("No skills match");
	}

	@Test
	void searchEmptyQuery(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search", ctx);
		// blank query returns all catalog entries
		assertThat(result).contains("Catalog results");
	}

	@Test
	void addMissingName(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("add", ctx);
		assertThat(result).contains("Usage: /skills add");
	}

	@Test
	void addUnknownSkill(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("add nonexistent-skill-xyz", ctx);
		assertThat(result).contains("not found in catalog");
	}

	@Test
	void removeMissingName(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("remove", ctx);
		assertThat(result).contains("Usage: /skills remove");
	}

	@Test
	void removeNotInstalled(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("remove nonexistent-skill-xyz", ctx);
		assertThat(result).contains("not installed");
	}

	@Test
	void infoMissingName(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("info", ctx);
		assertThat(result).contains("Usage: /skills info");
	}

	@Test
	void searchShowsMavenBadge(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search spring-boot", ctx);
		// spring-boot has skillsjars coordinates
		assertThat(result).contains("[maven]").contains("spring-boot");
	}

	@Test
	void searchShowsNoMavenBadgeForNonMavenSkills(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search dr-jskill", ctx);
		// dr-jskill has skillsjars: null
		assertThat(result).contains("dr-jskill").doesNotContain("[maven] dr-jskill");
	}

	@Test
	void infoCatalogSkillShowsBothInstallPaths(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		// spring-boot is in catalog but not installed locally
		String result = command.execute("info spring-boot", ctx);
		assertThat(result).contains("spring-boot")
			.contains("/skills add spring-boot")
			.contains("<dependency>")
			.contains("com.skillsjars")
			.contains("By: Siva Prasad Reddy");
	}

	@Test
	void infoCatalogSkillWithoutMavenShowsOnlyLoopyPath(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("info dr-jskill", ctx);
		assertThat(result).contains("dr-jskill").contains("/skills add dr-jskill").doesNotContain("<dependency>");
	}

	@Test
	void searchFooterShowsInfoHint(@TempDir Path tempDir) {
		var ctx = new CommandContext(tempDir, () -> {
		});
		String result = command.execute("search spring", ctx);
		assertThat(result).contains("/skills info <name>");
	}

	private void createSkill(Path root, String name, String description) throws IOException {
		Path skillDir = root.resolve(".claude/skills/" + name);
		Files.createDirectories(skillDir);
		Files.writeString(skillDir.resolve("SKILL.md"), """
				---
				name: %s
				description: %s
				---

				# %s

				Skill body content.
				""".formatted(name, description, name));
	}

}
