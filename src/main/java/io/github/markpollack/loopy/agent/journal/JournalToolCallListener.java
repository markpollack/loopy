package io.github.markpollack.loopy.agent.journal;

import io.github.markpollack.journal.Run;
import io.github.markpollack.journal.event.ToolCallEvent;
import io.github.markpollack.loopy.agent.core.ToolCallListener;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.time.Duration;
import java.util.Map;

/**
 * Bridges {@link ToolCallListener} events to journal-core {@link ToolCallEvent}s.
 */
public class JournalToolCallListener implements ToolCallListener {

	private final Run run;

	public JournalToolCallListener(Run run) {
		this.run = run;
	}

	@Override
	public void onToolExecutionCompleted(String runId, int turn, AssistantMessage.ToolCall toolCall, String result,
			Duration duration) {
		run.logEvent(ToolCallEvent.success(toolCall.name(), Map.of("arguments", toolCall.arguments()), result,
				duration.toMillis()));
	}

	@Override
	public void onToolExecutionFailed(String runId, int turn, AssistantMessage.ToolCall toolCall, Throwable error,
			Duration duration) {
		run.logEvent(ToolCallEvent.failure(toolCall.name(), Map.of("arguments", toolCall.arguments()),
				error.getMessage(), duration.toMillis()));
	}

}
