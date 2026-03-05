package io.github.markpollack.loopy.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEntryTest {

	@Test
	void userEntry() {
		ChatEntry entry = ChatEntry.user("hello");
		assertThat(entry.role()).isEqualTo(ChatEntry.Role.USER);
		assertThat(entry.content()).isEqualTo("hello");
	}

	@Test
	void assistantEntry() {
		ChatEntry entry = ChatEntry.assistant("response");
		assertThat(entry.role()).isEqualTo(ChatEntry.Role.ASSISTANT);
		assertThat(entry.content()).isEqualTo("response");
	}

	@Test
	void systemEntry() {
		ChatEntry entry = ChatEntry.system("command output");
		assertThat(entry.role()).isEqualTo(ChatEntry.Role.SYSTEM);
		assertThat(entry.content()).isEqualTo("command output");
	}

}
