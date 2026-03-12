package io.github.markpollack.loopy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ContextInjectionTest {

	@TempDir
	Path workDir;

	@Test
	void returnsNullWhenNeitherFileExists() {
		assertThat(LoopyCommand.buildContextInjection(workDir, "base")).isNull();
	}

	@Test
	void appendsAgentsMdWhenPresent() throws IOException {
		Files.writeString(workDir.resolve("AGENTS.md"), "agents instructions");

		String result = LoopyCommand.buildContextInjection(workDir, "base");

		assertThat(result).startsWith("base").contains("AGENTS.md").contains("agents instructions");
	}

	@Test
	void appendsClaudeMdWhenPresent() throws IOException {
		Files.writeString(workDir.resolve("CLAUDE.md"), "claude instructions");

		String result = LoopyCommand.buildContextInjection(workDir, "base");

		assertThat(result).startsWith("base").contains("CLAUDE.md").contains("claude instructions");
	}

	@Test
	void appendsBothFilesAgentsMdFirst() throws IOException {
		Files.writeString(workDir.resolve("AGENTS.md"), "agents instructions");
		Files.writeString(workDir.resolve("CLAUDE.md"), "claude instructions");

		String result = LoopyCommand.buildContextInjection(workDir, "base");

		assertThat(result).startsWith("base").contains("AGENTS.md").contains("CLAUDE.md");
		assertThat(result.indexOf("AGENTS.md")).isLessThan(result.indexOf("CLAUDE.md"));
	}

}
