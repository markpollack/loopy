package io.github.markpollack.loopy.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.loopy.command.CommandContext;

import static org.assertj.core.api.Assertions.assertThat;

class ForgeAgentCommandTest {

	@Test
	void nameAndDescription() {
		var cmd = new ForgeAgentCommand();
		assertThat(cmd.name()).isEqualTo("forge-agent");
		assertThat(cmd.description()).contains("agent experiment");
		assertThat(cmd.requiresArguments()).isTrue();
	}

	@Test
	void missingArgsReturnsUsage() {
		var cmd = new ForgeAgentCommand();
		var ctx = new CommandContext(Path.of("/tmp"), () -> {
		});
		assertThat(cmd.execute("", ctx)).contains("Usage");
		assertThat(cmd.execute(null, ctx)).contains("Usage");
	}

	@Test
	void missingBriefReturnsError() {
		var cmd = new ForgeAgentCommand();
		var ctx = new CommandContext(Path.of("/tmp"), () -> {
		});
		assertThat(cmd.execute("--output /tmp/out", ctx)).contains("--brief");
	}

	@Test
	void parsesArgumentsAndInvokesForge(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: test-experiment
				package: org.test.experiment
				groupId: org.test
				artifactId: test-experiment
				""");

		var clonerCalled = new boolean[] { false };
		var customizerCalled = new boolean[] { false };

		TemplateCloner recordingCloner = new TemplateCloner() {
			@Override
			public void cloneTemplate(String templateRepoUrl, Path outputDir) {
				clonerCalled[0] = true;
				try {
					Files.createDirectories(outputDir);
				}
				catch (IOException ex) {
					throw new java.io.UncheckedIOException(ex);
				}
			}
		};

		TemplateCustomizer recordingCustomizer = new TemplateCustomizer() {
			@Override
			public void customize(ExperimentBrief brief, Path projectDir) {
				customizerCalled[0] = true;
			}
		};

		var cmd = new ForgeAgentCommand(recordingCloner, recordingCustomizer, "https://example.com/template.git", null,
				null);
		Path outputDir = tempDir.resolve("output");
		var ctx = new CommandContext(tempDir, () -> {
		});

		String result = cmd.execute("--brief test-brief.yaml --output " + outputDir, ctx);

		assertThat(result).contains("scaffolded");
		assertThat(clonerCalled[0]).isTrue();
		assertThat(customizerCalled[0]).isTrue();
	}

	@Test
	void defaultOutputDirDerivedFromBriefName(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("my-brief.yaml");
		Files.writeString(briefPath, """
				name: my-cool-experiment
				""");

		TemplateCloner noopCloner = new TemplateCloner() {
			@Override
			public void cloneTemplate(String templateRepoUrl, Path outputDir) {
				assertThat(outputDir).isEqualTo(tempDir.resolve("my-cool-experiment"));
				try {
					Files.createDirectories(outputDir);
				}
				catch (IOException ex) {
					throw new java.io.UncheckedIOException(ex);
				}
			}
		};

		TemplateCustomizer noopCustomizer = new TemplateCustomizer() {
			@Override
			public void customize(ExperimentBrief brief, Path projectDir) {
			}
		};

		var cmd = new ForgeAgentCommand(noopCloner, noopCustomizer, "https://example.com/template.git", null, null);
		var ctx = new CommandContext(tempDir, () -> {
		});

		String result = cmd.execute("--brief my-brief.yaml", ctx);
		assertThat(result).contains("scaffolded");
	}

	@Test
	void invalidBriefReturnsError(@TempDir Path tempDir) {
		var cmd = new ForgeAgentCommand();
		var ctx = new CommandContext(tempDir, () -> {
		});

		String result = cmd.execute("--brief nonexistent.yaml", ctx);
		assertThat(result).startsWith("Error:");
	}

	@Test
	void noKbFlagParsed(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: test-experiment
				""");

		TemplateCloner noopCloner = new TemplateCloner() {
			@Override
			public void cloneTemplate(String templateRepoUrl, Path outputDir) {
				try {
					Files.createDirectories(outputDir);
				}
				catch (IOException ex) {
					throw new java.io.UncheckedIOException(ex);
				}
			}
		};

		TemplateCustomizer noopCustomizer = new TemplateCustomizer() {
			@Override
			public void customize(ExperimentBrief brief, Path projectDir) {
			}
		};

		// With --no-kb, no KB bootstrapping message should appear
		var cmd = new ForgeAgentCommand(noopCloner, noopCustomizer, "https://example.com/template.git", null, null);
		var ctx = new CommandContext(tempDir, () -> {
		});

		String result = cmd.execute("--brief test-brief.yaml --no-kb", ctx);
		assertThat(result).contains("scaffolded");
		assertThat(result).doesNotContain("KB bootstrap");
	}

	@Test
	void usageIncludesNoKbFlag() {
		var cmd = new ForgeAgentCommand();
		var ctx = new CommandContext(Path.of("/tmp"), () -> {
		});
		assertThat(cmd.execute("", ctx)).contains("--no-kb");
	}

	@Test
	void kbBootstrapSkippedWhenNoChatModel(@TempDir Path tempDir) throws IOException {
		Path briefPath = tempDir.resolve("test-brief.yaml");
		Files.writeString(briefPath, """
				name: test-experiment
				agent:
				  description: test agent
				  goal: test goal
				benchmark:
				  task: test task
				""");

		TemplateCloner noopCloner = new TemplateCloner() {
			@Override
			public void cloneTemplate(String templateRepoUrl, Path outputDir) {
				try {
					Files.createDirectories(outputDir);
				}
				catch (IOException ex) {
					throw new java.io.UncheckedIOException(ex);
				}
			}
		};

		TemplateCustomizer noopCustomizer = new TemplateCustomizer() {
			@Override
			public void customize(ExperimentBrief brief, Path projectDir) {
			}
		};

		// No ChatModel provided — KB phase should be silently skipped
		var cmd = new ForgeAgentCommand(noopCloner, noopCustomizer, "https://example.com/template.git", null, null);
		var ctx = new CommandContext(tempDir, () -> {
		});

		String result = cmd.execute("--brief test-brief.yaml --no-llm", ctx);
		assertThat(result).contains("scaffolded");
		// No KB message because chatModel is null
		assertThat(result).doesNotContain("KB bootstrap");
	}

}
