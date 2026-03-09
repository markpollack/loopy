package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.ToolCallListener;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.List;

/**
 * Fans out {@link ToolCallListener} events to multiple delegates.
 */
public class CompositeToolCallListener implements ToolCallListener {

	private final List<ToolCallListener> delegates;

	public CompositeToolCallListener(ToolCallListener... delegates) {
		this.delegates = List.of(delegates);
	}

	@Override
	public void onToolCallsRequested(String runId, int turn, List<AssistantMessage.ToolCall> toolCalls) {
		for (var d : delegates) {
			d.onToolCallsRequested(runId, turn, toolCalls);
		}
	}

	@Override
	public void onToolExecutionStarted(String runId, int turn, AssistantMessage.ToolCall toolCall) {
		for (var d : delegates) {
			d.onToolExecutionStarted(runId, turn, toolCall);
		}
	}

	@Override
	public void onToolExecutionCompleted(String runId, int turn, AssistantMessage.ToolCall toolCall, String result,
			Duration duration) {
		for (var d : delegates) {
			d.onToolExecutionCompleted(runId, turn, toolCall, result, duration);
		}
	}

	@Override
	public void onToolExecutionFailed(String runId, int turn, AssistantMessage.ToolCall toolCall, Throwable error,
			Duration duration) {
		for (var d : delegates) {
			d.onToolExecutionFailed(runId, turn, toolCall, error, duration);
		}
	}

	@Override
	public void onToolCallsCompleted(String runId, int turn, int toolCallCount, int successCount,
			Duration totalDuration) {
		for (var d : delegates) {
			d.onToolCallsCompleted(runId, turn, toolCallCount, successCount, totalDuration);
		}
	}

}
