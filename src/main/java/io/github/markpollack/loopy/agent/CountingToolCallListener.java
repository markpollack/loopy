package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.ToolCallListener;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ToolCallListener that counts tool executions and delegates to another listener.
 * <p>
 * This enables tracking toolCallsExecuted in MiniAgentResult while still getting logging
 * or other behavior from the delegate.
 */
public class CountingToolCallListener implements ToolCallListener {

	private final ToolCallListener delegate;

	private final AtomicInteger toolCallCount = new AtomicInteger(0);

	public CountingToolCallListener() {
		this(new LoggingToolCallListener());
	}

	public CountingToolCallListener(ToolCallListener delegate) {
		this.delegate = delegate;
	}

	@Override
	public void onToolExecutionStarted(String runId, int turn, AssistantMessage.ToolCall toolCall) {
		toolCallCount.incrementAndGet();
		if (delegate != null) {
			delegate.onToolExecutionStarted(runId, turn, toolCall);
		}
	}

	@Override
	public void onToolExecutionCompleted(String runId, int turn, AssistantMessage.ToolCall toolCall, String result,
			Duration duration) {
		if (delegate != null) {
			delegate.onToolExecutionCompleted(runId, turn, toolCall, result, duration);
		}
	}

	@Override
	public void onToolExecutionFailed(String runId, int turn, AssistantMessage.ToolCall toolCall, Throwable error,
			Duration duration) {
		if (delegate != null) {
			delegate.onToolExecutionFailed(runId, turn, toolCall, error, duration);
		}
	}

	/** Get the count of tool calls executed. */
	public int getToolCallCount() {
		return toolCallCount.get();
	}

	/** Reset the counter (for reuse). */
	public void reset() {
		toolCallCount.set(0);
	}

}
