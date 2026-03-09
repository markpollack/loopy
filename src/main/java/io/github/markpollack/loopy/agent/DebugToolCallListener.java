package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.ToolCallListener;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.PrintStream;
import java.time.Duration;

/**
 * User-facing verbose tool call listener for {@code --debug} mode.
 * <p>
 * Prints tool name, args summary, duration, and untruncated result to stderr.
 */
public class DebugToolCallListener implements ToolCallListener {

	private final PrintStream out;

	public DebugToolCallListener() {
		this(System.err);
	}

	public DebugToolCallListener(PrintStream out) {
		this.out = out;
	}

	@Override
	public void onToolExecutionCompleted(String runId, int turn, AssistantMessage.ToolCall toolCall, String result,
			Duration duration) {
		out.printf("  [turn %d] %s (%dms)%n", turn, toolCall.name(), duration.toMillis());
		String args = summarizeArgs(toolCall.arguments());
		if (!args.isEmpty()) {
			out.println("    args: " + args);
		}
		if (result != null && !result.isBlank()) {
			out.println("    result: " + truncate(result, 200));
		}
	}

	@Override
	public void onToolExecutionFailed(String runId, int turn, AssistantMessage.ToolCall toolCall, Throwable error,
			Duration duration) {
		out.printf("  [turn %d] %s FAILED (%dms): %s%n", turn, toolCall.name(), duration.toMillis(),
				error.getMessage());
	}

	private String summarizeArgs(String args) {
		if (args == null || args.isBlank()) {
			return "";
		}
		String cleaned = args.replace("\n", " ").trim();
		return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120) + "...";
	}

	private String truncate(String s, int max) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		String cleaned = s.replace("\n", " ").trim();
		return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "...";
	}

}
