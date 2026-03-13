package io.github.markpollack.loopy.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

	@TempDir
	Path tempDir;

	private SessionStore store() {
		return new SessionStore(tempDir);
	}

	private SessionStore.SessionMetadata meta() {
		return new SessionStore.SessionMetadata("/home/mark/projects/foo", "claude-sonnet-4-6", "anthropic");
	}

	// --- round-trip ---

	@Test
	void roundTripUserMessage() {
		var messages = List.<Message>of(new UserMessage("fix the bug"));
		var store = store();
		String id = store.save(messages, meta(), null);
		var loaded = store.load(id);
		assertThat(loaded).isPresent();
		assertThat(loaded.get()).hasSize(1);
		assertThat(loaded.get().get(0).getText()).isEqualTo("fix the bug");
	}

	@Test
	void roundTripAssistantTextMessage() {
		var messages = List.<Message>of(new UserMessage("hello"), new AssistantMessage("world"));
		var store = store();
		String id = store.save(messages, meta(), null);
		var loaded = store.load(id).orElseThrow();
		assertThat(loaded).hasSize(2);
		assertThat(loaded.get(1)).isInstanceOf(AssistantMessage.class);
		assertThat(loaded.get(1).getText()).isEqualTo("world");
	}

	@Test
	void roundTripAssistantWithToolCalls() {
		var toolCall = new AssistantMessage.ToolCall("tc1", "function", "Read", "{\"path\":\"/foo.java\"}");
		var assistant = AssistantMessage.builder().content("checking...").toolCalls(List.of(toolCall)).build();
		var messages = List.<Message>of(new UserMessage("task"), assistant);
		var store = store();
		String id = store.save(messages, meta(), null);
		var loaded = store.load(id).orElseThrow();
		var am = (AssistantMessage) loaded.get(1);
		assertThat(am.hasToolCalls()).isTrue();
		assertThat(am.getToolCalls().get(0).name()).isEqualTo("Read");
		assertThat(am.getToolCalls().get(0).arguments()).isEqualTo("{\"path\":\"/foo.java\"}");
	}

	@Test
	void roundTripToolResponseMessage() {
		var response = new ToolResponseMessage.ToolResponse("tc1", "Read", "file contents here");
		var trm = ToolResponseMessage.builder().responses(List.of(response)).build();
		var messages = List.<Message>of(new UserMessage("task"), trm);
		var store = store();
		String id = store.save(messages, meta(), null);
		var loaded = store.load(id).orElseThrow();
		var rm = (ToolResponseMessage) loaded.get(1);
		assertThat(rm.getResponses().get(0).id()).isEqualTo("tc1");
		assertThat(rm.getResponses().get(0).responseData()).isEqualTo("file contents here");
	}

	// --- slug / ID ---

	@Test
	void slugFromFirstUserMessage() {
		var messages = List.<Message>of(new UserMessage("fix the bug in Main.java please"));
		String id = store().save(messages, meta(), null);
		// timestamp prefix + first 4 words
		assertThat(id).contains("fix-the-bug-in");
	}

	@Test
	void customNameUsedAsSlug() {
		var messages = List.<Message>of(new UserMessage("anything"));
		String id = store().save(messages, meta(), "My Custom Name");
		assertThat(id).contains("my-custom-name");
	}

	@Test
	void slugifyRemovesPunctuation() {
		assertThat(SessionStore.slugify("Hello, World! How are you?")).isEqualTo("hello-world-how-are");
	}

	@Test
	void slugifyLimitsToFourWords() {
		assertThat(SessionStore.slugify("one two three four five six")).isEqualTo("one-two-three-four");
	}

	// --- list ---

	@Test
	void listReturnsMostRecentFirst() throws IOException, InterruptedException {
		var store = store();
		var messages = List.<Message>of(new UserMessage("first session"));
		store.save(messages, meta(), "first");
		Thread.sleep(1100); // ensure distinct timestamps
		store.save(List.of(new UserMessage("second session")), meta(), "second");
		var summaries = store.list();
		assertThat(summaries).hasSize(2);
		assertThat(summaries.get(0).firstMessage()).isEqualTo("second session");
	}

	@Test
	void listReturnsEmptyWhenNothingSaved() {
		assertThat(store().list()).isEmpty();
	}

	// --- load ---

	@Test
	void loadByIdPrefixWorks() {
		var messages = List.<Message>of(new UserMessage("test"));
		var store = store();
		String id = store.save(messages, meta(), "testprefix");
		// load by prefix (timestamp only)
		String prefix = id.substring(0, 8); // yyyyMMdd
		var loaded = store.load(prefix);
		assertThat(loaded).isPresent();
	}

	@Test
	void loadReturnsEmptyForUnknownId() {
		assertThat(store().load("does-not-exist")).isEmpty();
	}

}
