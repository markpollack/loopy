package io.github.markpollack.loopy.agent.loop;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLoopAdvisorCompactionTest {

	@Test
	void estimateTokenCountSumsTextLengthsDividedByFour() {
		List<Message> messages = List.of(new UserMessage("abcd"), // 4 chars = 1 token
				new AssistantMessage("abcdefgh"), // 8 chars = 2 tokens
				new SystemMessage("ab") // 2 chars = 0 tokens (integer division)
		);
		assertThat(AgentLoopAdvisor.estimateTokenCount(messages)).isEqualTo(3); // 14/4
	}

	@Test
	void estimateTokenCountHandlesNullText() {
		List<Message> messages = List.of(new UserMessage("hello"), new AssistantMessage(null));
		assertThat(AgentLoopAdvisor.estimateTokenCount(messages)).isEqualTo(1); // 5/4
	}

	@Test
	void shouldCompactReturnsFalseWhenNoCompactionModel() {
		var advisor = buildAdvisor(null, 1000, 0.5);
		var request = buildRequest(List.of(new UserMessage("x".repeat(2000))));
		assertThat(advisor.shouldCompact(request)).isFalse();
	}

	@Test
	void shouldCompactReturnsTrueWhenOverThreshold() {
		var mockModel = mock(ChatModel.class);
		// contextLimit=1000, threshold=0.5 → triggers at 500 estimated tokens = 2000
		// chars
		var advisor = buildAdvisor(mockModel, 1000, 0.5);
		var request = buildRequest(List.of(new UserMessage("x".repeat(2400)))); // 600
																				// tokens
		assertThat(advisor.shouldCompact(request)).isTrue();
	}

	@Test
	void shouldCompactReturnsFalseWhenUnderThreshold() {
		var mockModel = mock(ChatModel.class);
		var advisor = buildAdvisor(mockModel, 1000, 0.5);
		var request = buildRequest(List.of(new UserMessage("x".repeat(800)))); // 200
																				// tokens
		assertThat(advisor.shouldCompact(request)).isFalse();
	}

	@Test
	void compactMessagesPreservesSystemMessages() {
		var compactionModel = mockCompactionModel("Summary of conversation");
		var advisor = buildAdvisor(compactionModel, 100, 0.5);

		// Initialize loop state (required by compactMessages)
		initLoopState(advisor);

		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("You are a helpful assistant"));
		// Add enough conversation messages to allow splitting
		for (int i = 0; i < 10; i++) {
			messages.add(new UserMessage("question " + i));
			messages.add(new AssistantMessage("answer " + i));
		}

		var request = buildRequest(messages);
		var result = advisor.compactMessages(request);
		var resultMessages = result.prompt().getInstructions();

		// First message should be the original system message
		assertThat(resultMessages.get(0)).isInstanceOf(SystemMessage.class);
		assertThat(resultMessages.get(0).getText()).isEqualTo("You are a helpful assistant");

		// Second should be the compaction summary (also a SystemMessage)
		assertThat(resultMessages.get(1)).isInstanceOf(SystemMessage.class);
		assertThat(resultMessages.get(1).getText()).contains("Summary of conversation");
	}

	@Test
	void compactMessagesPreservesRecent30Percent() {
		var compactionModel = mockCompactionModel("Compacted history");
		var advisor = buildAdvisor(compactionModel, 100, 0.5);
		initLoopState(advisor);

		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("system"));
		// 10 conversation messages
		for (int i = 0; i < 10; i++) {
			messages.add(new UserMessage("msg-" + i));
		}

		var request = buildRequest(messages);
		var result = advisor.compactMessages(request);
		var resultMessages = result.prompt().getInstructions();

		// 10 conversation messages → preserve 30% = 3 messages (last 3)
		// Result: system + summary + 3 preserved = 5 messages
		assertThat(resultMessages).hasSize(5);

		// Last 3 should be the original recent messages
		assertThat(resultMessages.get(2).getText()).isEqualTo("msg-7");
		assertThat(resultMessages.get(3).getText()).isEqualTo("msg-8");
		assertThat(resultMessages.get(4).getText()).isEqualTo("msg-9");
	}

	@Test
	void compactMessagesSkipsWhenTooFewMessages() {
		var compactionModel = mockCompactionModel("summary");
		var advisor = buildAdvisor(compactionModel, 100, 0.5);
		initLoopState(advisor);

		// Only 2 conversation messages (below threshold of 3)
		List<Message> messages = List.of(new SystemMessage("sys"), new UserMessage("q"), new AssistantMessage("a"));
		var request = buildRequest(messages);
		var result = advisor.compactMessages(request);

		// Should return unchanged
		assertThat(result.prompt().getInstructions()).hasSize(3);
	}

	@Test
	void compactMessagesSurvivesCompactionFailure() {
		var failingModel = mock(ChatModel.class);
		when(failingModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Model unavailable"));
		var advisor = buildAdvisor(failingModel, 100, 0.5);
		initLoopState(advisor);

		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage("sys"));
		for (int i = 0; i < 10; i++) {
			messages.add(new UserMessage("msg-" + i));
		}

		var request = buildRequest(messages);
		var result = advisor.compactMessages(request);

		// Should return original request unchanged
		assertThat(result.prompt().getInstructions()).hasSize(11);
	}

	// --- Helpers ---

	private AgentLoopAdvisor buildAdvisor(ChatModel compactionModel, int contextLimit, double threshold) {
		return AgentLoopAdvisor.builder()
			.toolCallingManager(DefaultToolCallingManager.builder().build())
			.compactionModel(compactionModel)
			.contextLimit(contextLimit)
			.compactionThreshold(threshold)
			.build();
	}

	private ChatClientRequest buildRequest(List<Message> messages) {
		return ChatClientRequest.builder().prompt(new Prompt(messages)).build();
	}

	private void initLoopState(AgentLoopAdvisor advisor) {
		// Use reflection-free approach: call doInitializeLoop indirectly won't work,
		// so we test compactMessages which handles null loopState gracefully
		// The loopState ThreadLocal is set during doInitializeLoop; for unit tests,
		// compactMessages checks for null state and handles it
	}

	private ChatModel mockCompactionModel(String summaryResponse) {
		var chatModel = mock(ChatModel.class);
		var generation = new Generation(new AssistantMessage(summaryResponse));
		var chatResponse = new ChatResponse(List.of(generation));
		when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
		return chatModel;
	}

}
