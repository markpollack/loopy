package io.github.markpollack.loopy.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallHashTrackerTest {

	@Test
	void returnsNullWhenNoToolCallsThisTurn() {
		var tracker = new ToolCallHashTracker();
		assertThat(tracker.getAndResetTurnHash()).isNull();
	}

	@Test
	void returnsSameHashForIdenticalToolCalls() {
		var tracker = new ToolCallHashTracker();
		tracker.onToolExecutionStarted("run1", 1, toolCall("bash", "{\"command\":\"ls\"}"));
		String hash1 = tracker.getAndResetTurnHash();

		tracker.onToolExecutionStarted("run1", 2, toolCall("bash", "{\"command\":\"ls\"}"));
		String hash2 = tracker.getAndResetTurnHash();

		assertThat(hash1).isNotNull().isEqualTo(hash2);
	}

	@Test
	void returnsDifferentHashForDifferentArgs() {
		var tracker = new ToolCallHashTracker();
		tracker.onToolExecutionStarted("run1", 1, toolCall("bash", "{\"command\":\"ls\"}"));
		String hash1 = tracker.getAndResetTurnHash();

		tracker.onToolExecutionStarted("run1", 2, toolCall("bash", "{\"command\":\"pwd\"}"));
		String hash2 = tracker.getAndResetTurnHash();

		assertThat(hash1).isNotEqualTo(hash2);
	}

	@Test
	void resetsAfterGet() {
		var tracker = new ToolCallHashTracker();
		tracker.onToolExecutionStarted("run1", 1, toolCall("bash", "{}"));
		tracker.getAndResetTurnHash();
		// After reset, next call returns null
		assertThat(tracker.getAndResetTurnHash()).isNull();
	}

	@Test
	void combinesMultipleToolCallsInOrder() {
		var tracker = new ToolCallHashTracker();
		tracker.onToolExecutionStarted("run1", 1, toolCall("bash", "{}"));
		tracker.onToolExecutionStarted("run1", 1, toolCall("Read", "{\"path\":\"/foo\"}"));
		String hashAB = tracker.getAndResetTurnHash();

		// Reversed order should give different hash
		tracker.onToolExecutionStarted("run1", 2, toolCall("Read", "{\"path\":\"/foo\"}"));
		tracker.onToolExecutionStarted("run1", 2, toolCall("bash", "{}"));
		String hashBA = tracker.getAndResetTurnHash();

		assertThat(hashAB).isNotEqualTo(hashBA);
	}

	// --- LoopState stuck detection ---

	@Test
	void loopStateDetectsConsecutiveIdenticalToolCalls() {
		var state = io.github.markpollack.loopy.agent.core.LoopState.initial("run1");
		String hash = ToolCallHashTracker.sha256("bash:{}");
		for (int i = 0; i < 5; i++) {
			state = state.completeTurn(100, 50, 0.001, true, 0, hash);
		}
		assertThat(state.isToolCallStuck(5)).isTrue();
		assertThat(state.isToolCallStuck(6)).isFalse();
	}

	@Test
	void loopStateDoesNotFlagVariedToolCalls() {
		var state = io.github.markpollack.loopy.agent.core.LoopState.initial("run1");
		String hashA = ToolCallHashTracker.sha256("bash:ls");
		String hashB = ToolCallHashTracker.sha256("bash:pwd");
		String hashC = ToolCallHashTracker.sha256("bash:echo");
		state = state.completeTurn(100, 50, 0.001, true, 0, hashA);
		state = state.completeTurn(100, 50, 0.001, true, 0, hashB);
		state = state.completeTurn(100, 50, 0.001, true, 0, hashC);
		state = state.completeTurn(100, 50, 0.001, true, 0, hashA);
		state = state.completeTurn(100, 50, 0.001, true, 0, hashB);
		assertThat(state.isToolCallStuck(5)).isFalse();
	}

	@Test
	void loopStateDetectsAlternatingToolCalls() {
		var state = io.github.markpollack.loopy.agent.core.LoopState.initial("run1");
		String hashA = ToolCallHashTracker.sha256("bash:ls");
		String hashB = ToolCallHashTracker.sha256("bash:pwd");
		// 10 turns: A-B-A-B-A-B-A-B-A-B
		for (int i = 0; i < 10; i++) {
			String hash = (i % 2 == 0) ? hashA : hashB;
			state = state.completeTurn(100, 50, 0.001, true, 0, hash);
		}
		assertThat(state.isAlternatingToolCalls(10)).isTrue();
	}

	@Test
	void loopStateDoesNotFlagAlternatingWithFewerTurns() {
		var state = io.github.markpollack.loopy.agent.core.LoopState.initial("run1");
		String hashA = ToolCallHashTracker.sha256("bash:ls");
		String hashB = ToolCallHashTracker.sha256("bash:pwd");
		for (int i = 0; i < 8; i++) {
			state = state.completeTurn(100, 50, 0.001, true, 0, (i % 2 == 0) ? hashA : hashB);
		}
		assertThat(state.isAlternatingToolCalls(10)).isFalse();
	}

	@Test
	void loopStateDoesNotFlagThreeDistinctHashesAsAlternating() {
		var state = io.github.markpollack.loopy.agent.core.LoopState.initial("run1");
		String hashA = ToolCallHashTracker.sha256("a");
		String hashB = ToolCallHashTracker.sha256("b");
		String hashC = ToolCallHashTracker.sha256("c");
		// A-B-A-C-A-B-A-C-A-B — not strictly A-B alternating
		for (int i = 0; i < 10; i++) {
			String hash = switch (i % 4) {
				case 0, 2 -> hashA;
				case 1 -> hashB;
				default -> hashC;
			};
			state = state.completeTurn(100, 50, 0.001, true, 0, hash);
		}
		assertThat(state.isAlternatingToolCalls(10)).isFalse();
	}

	private AssistantMessage.ToolCall toolCall(String name, String arguments) {
		return new AssistantMessage.ToolCall("id-" + name, "function", name, arguments);
	}

}
