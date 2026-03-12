package io.github.markpollack.loopy.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraceTurnTest {

	@Test
	void returnsGraceOutputWhenModelResponds() {
		var model = mockModel("Here is my best summary of progress so far.");
		String result = MiniAgent.tryGraceTurn(model, "do the task", "partial output", "system prompt");
		assertThat(result).isEqualTo("Here is my best summary of progress so far.");
	}

	@Test
	void returnsNullWhenPartialOutputIsNullStillWorks() {
		var model = mockModel("Summary with no prior output.");
		String result = MiniAgent.tryGraceTurn(model, "do the task", null, "system prompt");
		assertThat(result).isEqualTo("Summary with no prior output.");
	}

	@Test
	void returnsNullWhenModelReturnsBlank() {
		var model = mockModel("   ");
		String result = MiniAgent.tryGraceTurn(model, "do the task", "partial", "system prompt");
		assertThat(result).isNull();
	}

	@Test
	void returnsNullWhenModelThrows() {
		var model = mock(ChatModel.class);
		when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("API unavailable"));
		String result = MiniAgent.tryGraceTurn(model, "do the task", "partial", "system prompt");
		assertThat(result).isNull();
	}

	@Test
	void returnsNullWhenModelReturnsNullResponse() {
		var model = mock(ChatModel.class);
		when(model.call(any(Prompt.class))).thenReturn(null);
		String result = MiniAgent.tryGraceTurn(model, "do the task", "partial", "system prompt");
		assertThat(result).isNull();
	}

	private ChatModel mockModel(String text) {
		var model = mock(ChatModel.class);
		var output = new AssistantMessage(text);
		var generation = new Generation(output);
		var response = new ChatResponse(java.util.List.of(generation));
		when(model.call(any(Prompt.class))).thenReturn(response);
		return model;
	}

}
