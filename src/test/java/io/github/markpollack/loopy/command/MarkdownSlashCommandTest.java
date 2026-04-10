package io.github.markpollack.loopy.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSlashCommandTest {

	@TempDir
	Path tempDir;

	@Test
	void parsesYamlFrontMatter() throws IOException {
		Path file = writeCommand("greet.md", """
				---
				name: greet
				description: "Say hello to someone"
				---

				# Greet

				Say hello to $ARGUMENTS
				""");

		MarkdownSlashCommand cmd = MarkdownSlashCommand.fromFile(file);
		assertThat(cmd).isNotNull();
		assertThat(cmd.name()).isEqualTo("greet");
		assertThat(cmd.description()).isEqualTo("Say hello to someone");
	}

	@Test
	void fallsBackToFilenameWhenNoFrontMatter() throws IOException {
		Path file = writeCommand("my-tool.md", """
				# My Tool

				Do the thing with $ARGUMENTS
				""");

		MarkdownSlashCommand cmd = MarkdownSlashCommand.fromFile(file);
		assertThat(cmd).isNotNull();
		assertThat(cmd.name()).isEqualTo("my-tool");
		assertThat(cmd.description()).isEmpty();
	}

	@Test
	void substitutesArguments() throws IOException {
		Path file = writeCommand("echo.md", """
				---
				name: echo
				description: "Echo back"
				---

				Please echo: $ARGUMENTS
				""");

		MarkdownSlashCommand cmd = MarkdownSlashCommand.fromFile(file);
		assertThat(cmd).isNotNull();

		// Wire up a fake agent delegate that returns what it receives
		CommandContext ctx = new CommandContext(this.tempDir, () -> {
		}, model -> {
		}, prompt -> prompt);

		String result = cmd.execute("hello world", ctx);
		assertThat(result).contains("Please echo: hello world");
		assertThat(result).doesNotContain("$ARGUMENTS");
	}

	@Test
	void emptyArgsSubstitutesBlank() throws IOException {
		Path file = writeCommand("run.md", """
				---
				name: run
				description: "Run it"
				---

				Execute with args: [$ARGUMENTS]
				""");

		MarkdownSlashCommand cmd = MarkdownSlashCommand.fromFile(file);
		assertThat(cmd).isNotNull();

		CommandContext ctx = new CommandContext(this.tempDir, () -> {
		}, model -> {
		}, prompt -> prompt);

		String result = cmd.execute("", ctx);
		assertThat(result).contains("Execute with args: []");
	}

	@Test
	void returnsNullForNonMdFile() throws IOException {
		Path file = this.tempDir.resolve("readme.txt");
		Files.writeString(file, "not a command");

		assertThat(MarkdownSlashCommand.fromFile(file)).isNull();
	}

	@Test
	void returnsNullForMissingFile() {
		assertThat(MarkdownSlashCommand.fromFile(this.tempDir.resolve("missing.md"))).isNull();
	}

	@Test
	void contextTypeIsAssistant() throws IOException {
		Path file = writeCommand("test.md", """
				---
				name: test
				description: "Test"
				---

				Content
				""");

		MarkdownSlashCommand cmd = MarkdownSlashCommand.fromFile(file);
		assertThat(cmd).isNotNull();
		assertThat(cmd.contextType()).isEqualTo(SlashCommand.ContextType.ASSISTANT);
	}

	@Test
	void markdownCommandOverridesJavaCommand() throws IOException {
		Path file = writeCommand("help.md", """
				---
				name: help
				description: "Custom help from markdown"
				---

				Custom help content for $ARGUMENTS
				""");

		SlashCommandRegistry registry = new SlashCommandRegistry();
		registry.register(new HelpCommand(registry));

		// Markdown registered after — should overwrite
		MarkdownSlashCommand mdCmd = MarkdownSlashCommand.fromFile(file);
		assertThat(mdCmd).isNotNull();
		registry.register(mdCmd);

		CommandContext ctx = new CommandContext(this.tempDir, () -> {
		}, model -> {
		}, prompt -> prompt);

		var result = registry.dispatch("/help", ctx);
		assertThat(result).isPresent();
		assertThat(result.get()).contains("Custom help content");
	}

	private Path writeCommand(String filename, String content) throws IOException {
		Path file = this.tempDir.resolve(filename);
		Files.writeString(file, content);
		return file;
	}

}
