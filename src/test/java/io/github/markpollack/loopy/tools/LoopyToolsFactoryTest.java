package io.github.markpollack.loopy.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopyToolsFactoryTest {

	@Test
	void devProfileIncludesCoreTools(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("dev"), ctx);

		var names = toolNames(tools);
		assertThat(names).contains("bash", "Read", "Write", "Edit", "Glob", "Grep", "ListDirectory", "TodoWrite");
	}

	@Test
	void bootProfileIncludesBootTools(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("boot"), ctx);

		var names = toolNames(tools);
		assertThat(names).anyMatch(n -> n.toLowerCase().contains("boot"));
	}

	@Test
	void devAndBootProfilesCombined(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> devOnly = LoopyToolsFactory.toolsForProfiles(List.of("dev"), ctx);
		List<ToolCallback> bootOnly = LoopyToolsFactory.toolsForProfiles(List.of("boot"), ctx);
		List<ToolCallback> combined = LoopyToolsFactory.toolsForProfiles(List.of("dev", "boot"), ctx);

		assertThat(combined).hasSize(devOnly.size() + bootOnly.size());
	}

	@Test
	void headlessProfileExcludesAskUserQuestion(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("headless"), ctx);

		assertThat(toolNames(tools)).doesNotContain("AskUserQuestion");
	}

	@Test
	void devProfileExcludesBootTools(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("dev"), ctx);

		var names = toolNames(tools);
		assertThat(names).noneMatch(n -> n.toLowerCase().contains("boot"));
	}

	@Test
	void enterpriseProfileExcludesBashAndShell(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("enterprise"), ctx);

		var names = toolNames(tools);
		// No shell execution
		assertThat(names).doesNotContain("bash");
		// No TodoWrite (write operations beyond file-system)
		assertThat(names).doesNotContain("TodoWrite");
		// File-system, search, listing still available
		assertThat(names).contains("Glob", "Grep", "ListDirectory");
	}

	@Test
	void unknownProfileReturnsEmpty(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of("nonexistent"), ctx);

		assertThat(tools).isEmpty();
	}

	@Test
	void emptyProfileListReturnsEmpty(@TempDir Path dir) {
		var ctx = ToolFactoryContext.headless(dir, null, Duration.ofSeconds(30));

		List<ToolCallback> tools = LoopyToolsFactory.toolsForProfiles(List.of(), ctx);

		assertThat(tools).isEmpty();
	}

	private List<String> toolNames(List<ToolCallback> tools) {
		return tools.stream().map(t -> t.getToolDefinition().name()).toList();
	}

}
