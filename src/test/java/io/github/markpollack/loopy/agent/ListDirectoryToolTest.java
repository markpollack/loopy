package io.github.markpollack.loopy.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.ListDirectoryTool;

import static org.assertj.core.api.Assertions.assertThat;

class ListDirectoryToolTest {

	@TempDir
	Path tempDir;

	@Test
	void listsFilesAndDirs() throws IOException {
		Files.createDirectory(tempDir.resolve("src"));
		Files.createFile(tempDir.resolve("pom.xml"));
		Files.createFile(tempDir.resolve("README.md"));

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String result = tool.listDirectory(null, null, null);

		assertThat(result).contains("[dir]  src").contains("[file] pom.xml").contains("[file] README.md");
	}

	@Test
	void directoriesListedBeforeFiles() throws IOException {
		Files.createFile(tempDir.resolve("aaa.txt"));
		Files.createDirectory(tempDir.resolve("zzz"));

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String result = tool.listDirectory(null, null, null);

		int dirPos = result.indexOf("[dir]");
		int filePos = result.indexOf("[file]");
		assertThat(dirPos).isLessThan(filePos);
	}

	@Test
	void skipsIgnoredDirectories() throws IOException {
		Files.createDirectory(tempDir.resolve("target"));
		Files.createDirectory(tempDir.resolve("src"));
		Files.createDirectory(tempDir.resolve(".git"));

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String result = tool.listDirectory(null, null, null);

		assertThat(result).contains("src").doesNotContain("target").doesNotContain(".git");
	}

	@Test
	void recursesWithDepth() throws IOException {
		Path sub = Files.createDirectory(tempDir.resolve("sub"));
		Files.createFile(sub.resolve("nested.txt"));

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String depthOne = tool.listDirectory(null, 1, null);
		String depthTwo = tool.listDirectory(null, 2, null);

		assertThat(depthOne).doesNotContain("nested.txt");
		assertThat(depthTwo).contains("nested.txt");
	}

	@Test
	void respectsLimit() throws IOException {
		for (int i = 0; i < 10; i++) {
			Files.createFile(tempDir.resolve("file" + i + ".txt"));
		}

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String result = tool.listDirectory(null, null, 3);

		assertThat(result).contains("limit of 3 reached");
	}

	@Test
	void returnsErrorForNonExistentPath() {
		var tool = ListDirectoryTool.builder().build();
		String result = tool.listDirectory("/nonexistent/path/xyz", null, null);

		assertThat(result).startsWith("Error: Path does not exist");
	}

	@Test
	void usesWorkingDirectoryWhenPathOmitted() throws IOException {
		Files.createFile(tempDir.resolve("hello.txt"));

		var tool = ListDirectoryTool.builder().workingDirectory(tempDir).build();
		String result = tool.listDirectory(null, null, null);

		assertThat(result).contains("hello.txt");
	}

}
