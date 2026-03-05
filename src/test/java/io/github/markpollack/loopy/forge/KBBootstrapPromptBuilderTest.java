package io.github.markpollack.loopy.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class KBBootstrapPromptBuilderTest {

	@Test
	void systemPromptContainsDomainAndTask(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: code-coverage-experiment
				agent:
				  description: A code coverage analysis agent
				  goal: achieve 80% branch coverage
				benchmark:
				  task: add JUnit tests to improve coverage
				""");

		ExperimentBrief brief = ExperimentBrief.parse(briefPath);
		var builder = new KBBootstrapPromptBuilder();

		String prompt = builder.buildSystemPrompt(brief, "/tmp/project/knowledge");

		assertThat(prompt).contains("CodeCoverage");
		assertThat(prompt).contains("A code coverage analysis agent");
		assertThat(prompt).contains("achieve 80% branch coverage");
		assertThat(prompt).contains("add JUnit tests to improve coverage");
		assertThat(prompt).contains("/tmp/project/knowledge");
		assertThat(prompt).contains("web_search");
		assertThat(prompt).contains("smart_web_fetch");
		assertThat(prompt).contains("index.md");
	}

	@Test
	void systemPromptContainsKBFileStructure(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: test-experiment
				agent:
				  description: test
				  goal: test
				benchmark:
				  task: test
				""");

		ExperimentBrief brief = ExperimentBrief.parse(briefPath);
		var builder = new KBBootstrapPromptBuilder();

		String prompt = builder.buildSystemPrompt(brief, "/tmp/knowledge");

		assertThat(prompt).contains("Key Concepts");
		assertThat(prompt).contains("Patterns");
		assertThat(prompt).contains("Common Pitfalls");
		assertThat(prompt).contains("References");
		assertThat(prompt).contains("domain/");
	}

	@Test
	void userMessageContainsBriefContext(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: security-testing-experiment
				agent:
				  description: Security testing agent
				  goal: find and fix OWASP top 10 vulnerabilities
				benchmark:
				  task: scan Spring Boot apps for security issues
				""");

		ExperimentBrief brief = ExperimentBrief.parse(briefPath);
		var builder = new KBBootstrapPromptBuilder();

		String message = builder.buildUserMessage(brief);

		assertThat(message).contains("security-testing-experiment");
		assertThat(message).contains("find and fix OWASP top 10 vulnerabilities");
		assertThat(message).contains("scan Spring Boot apps for security issues");
	}

}
