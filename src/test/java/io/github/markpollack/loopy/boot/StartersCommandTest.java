package io.github.markpollack.loopy.boot;

import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.loopy.command.CommandContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartersCommandTest {

	@TempDir
	Path tempDir;

	@BeforeAll
	static void initTerminalInfo() {
		TerminalInfo.provide(() -> new TerminalInfo(false, null));
	}

	private final StartersCommand command = new StartersCommand();

	private CommandContext context() {
		return new CommandContext(tempDir, () -> {
		});
	}

	@Test
	void listShowsEmptyCatalogMessage() {
		String result = command.execute("list", context());
		assertThat(result).containsIgnoringCase("catalog");
	}

	@Test
	void listIsDefaultSubcommand() {
		String noArgs = command.execute("", context());
		String listArgs = command.execute("list", context());
		assertThat(noArgs).isEqualTo(listArgs);
	}

	@Test
	void searchOnEmptyCatalogReturnsMessage() {
		String result = command.execute("search jpa", context());
		assertThat(result).isNotBlank();
	}

	@Test
	void infoRequiresName() {
		String result = command.execute("info", context());
		assertThat(result).containsIgnoringCase("usage");
	}

	@Test
	void infoUnknownStarterReturnsNotFound() {
		String result = command.execute("info spring-ai-starter-unknown", context());
		assertThat(result).containsIgnoringCase("not found");
	}

	@Test
	void suggestWithNoPomReturnsError() {
		String result = command.execute("suggest", context());
		assertThat(result).containsIgnoringCase("pom.xml");
	}

	@Test
	void suggestWithPomExtractsArtifactIds() throws Exception {
		String pomContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <dependencies>
				    <dependency>
				      <groupId>org.springframework.boot</groupId>
				      <artifactId>spring-boot-starter-data-jpa</artifactId>
				    </dependency>
				    <dependency>
				      <groupId>org.springframework.boot</groupId>
				      <artifactId>spring-boot-starter-web</artifactId>
				    </dependency>
				  </dependencies>
				</project>
				""";
		Files.writeString(tempDir.resolve("pom.xml"), pomContent);

		String result = command.execute("suggest", context());
		// With empty catalog, no suggestions — but it should scan without error
		assertThat(result).isNotBlank().doesNotContain("No pom.xml found");
	}

	@Test
	void extractArtifactIdsFindsAllOccurrences() throws Exception {
		String pomContent = """
				<project>
				  <artifactId>my-app</artifactId>
				  <dependencies>
				    <dependency><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
				    <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>
				  </dependencies>
				</project>
				""";
		Path pomFile = tempDir.resolve("pom.xml");
		Files.writeString(pomFile, pomContent);

		List<String> ids = StartersCommand.extractArtifactIds(pomFile);
		assertThat(ids).contains("my-app", "spring-boot-starter-data-jpa", "spring-boot-starter-web");
	}

	@Test
	void unknownSubcommandShowsUsage() {
		String result = command.execute("bogus-subcommand", context());
		assertThat(result).containsIgnoringCase("unknown subcommand");
	}

}
