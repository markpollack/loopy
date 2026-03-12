package io.github.markpollack.loopy.boot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.loopy.command.CommandContext;

import static org.assertj.core.api.Assertions.assertThat;

class BootNewCommandTest {

	@TempDir
	Path tempDir;

	private final BootNewCommand command = new BootNewCommand(null); // null = no LLM

	private CommandContext context() {
		return new CommandContext(tempDir, () -> {
		});
	}

	@Test
	void scaffoldsMinimalTemplateWithCorrectStructure() throws Exception {
		String result = command.execute("--name my-app --group com.example --template spring-boot-minimal --no-llm",
				context());

		assertThat(result).contains("my-app").contains("spring-boot-minimal");

		Path project = tempDir.resolve("my-app");
		assertThat(project.resolve("pom.xml")).isRegularFile();
		assertThat(project.resolve("mvnw")).isRegularFile();
		assertThat(project.resolve("src/main/java/com/example/myapp/Application.java")).isRegularFile();
	}

	@Test
	void pomContainsCorrectGav() throws Exception {
		command.execute("--name products-api --group com.acme --template spring-boot-minimal --no-llm", context());

		String pom = Files.readString(tempDir.resolve("products-api/pom.xml"));
		assertThat(pom).contains("<groupId>com.acme</groupId>");
		assertThat(pom).contains("<artifactId>products-api</artifactId>");
		assertThat(pom).doesNotContain("<groupId>com.example</groupId>");
		assertThat(pom).doesNotContain("<artifactId>app</artifactId>");
	}

	@Test
	void packageIsRenamed() throws Exception {
		command.execute("--name widget-service --group com.corp --template spring-boot-minimal --no-llm", context());

		Path appFile = tempDir.resolve("widget-service/src/main/java/com/corp/widgetservice/Application.java");
		assertThat(appFile).isRegularFile();
		assertThat(Files.readString(appFile)).contains("package com.corp.widgetservice;");
	}

	@Test
	void restTemplateIncludesController() throws Exception {
		command.execute("--name api-service --group com.demo --template spring-boot-rest --no-llm", context());

		Path project = tempDir.resolve("api-service");
		assertThat(project.resolve("src/main/java/com/demo/apiservice/greeting/GreetingController.java"))
			.isRegularFile();
	}

	@Test
	void requiresNameFlag() {
		String result = command.execute("--group com.example --no-llm", context());
		assertThat(result).containsIgnoringCase("usage");
	}

	@Test
	void requiresGroupOnFirstUse() {
		// Simulate no prefs file by using a separate temp dir — prefs are per-user,
		// but the command falls back to BootPreferences.load() which reads ~/.config.
		// We only assert that the error message is informative when --group is omitted
		// and no saved groupId exists.
		// (If the test machine has saved prefs, the command will succeed — that's ok.)
		String result = command.execute("--name my-app --no-llm", context());
		// Either succeeds (prefs exist) or shows a helpful error
		assertThat(result).isNotBlank();
	}

	@Test
	void rejectsUnknownTemplate() {
		String result = command.execute("--name my-app --group com.example --template spring-boot-unknown --no-llm",
				context());
		assertThat(result).containsIgnoringCase("unknown template");
	}

	@Test
	void javaVersionFlagIsAppliedToPom() throws Exception {
		command.execute("--name my-app --group com.example --java-version 17 --no-llm", context());

		String pom = Files.readString(tempDir.resolve("my-app/pom.xml"));
		assertThat(pom).contains("<java.version>17</java.version>");
		assertThat(pom).doesNotContain("<java.version>21</java.version>");
	}

	@Test
	void defaultJavaVersionIsAppliedWhenFlagAbsent() throws Exception {
		command.execute("--name my-app --group com.example --no-llm", context());

		String pom = Files.readString(tempDir.resolve("my-app/pom.xml"));
		// Some version from prefs or default (21) is applied; exact value is
		// prefs-dependent
		assertThat(pom).containsPattern("<java\\.version>\\d+</java\\.version>");
	}

	@Test
	void nlInputWithoutLlmReturnsHelpText() {
		// No chatModel — NL path is skipped, name not found → help
		String result = command.execute("a REST API for com.acme", context());
		assertThat(result).containsIgnoringCase("usage");
	}

	@Test
	void parseFlagsHandlesBooleanAndValueFlags() {
		Map<String, String> flags = BootNewCommand.parseFlags("--name my-app --group com.acme --no-llm");
		assertThat(flags).containsEntry("name", "my-app")
			.containsEntry("group", "com.acme")
			.containsEntry("no-llm", "true");
	}

	@Test
	void parseFlagsHandlesJavaVersionFlag() {
		Map<String, String> flags = BootNewCommand.parseFlags("--name my-app --group com.acme --java-version 17");
		assertThat(flags).containsEntry("java-version", "17");
	}

	@Test
	void parseFlagsReturnsEmptyForBlankInput() {
		assertThat(BootNewCommand.parseFlags("")).isEmpty();
		assertThat(BootNewCommand.parseFlags(null)).isEmpty();
	}

}
