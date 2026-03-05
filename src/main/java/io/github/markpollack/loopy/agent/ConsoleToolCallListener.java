package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.ToolCallListener;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.PrintStream;
import java.time.Duration;

/**
 * A ToolCallListener that prints clean, human-readable progress to a PrintStream.
 */
public class ConsoleToolCallListener implements ToolCallListener {

	private final PrintStream out;

	public ConsoleToolCallListener() {
		this(System.err);
	}

	public ConsoleToolCallListener(PrintStream out) {
		this.out = out;
	}

	@Override
	public void onToolExecutionCompleted(String runId, int turn, AssistantMessage.ToolCall toolCall, String result,
			Duration duration) {
		String summary = summarize(toolCall.name(), toolCall.arguments(), result);
		out.println("  > " + summary);
	}

	@Override
	public void onToolExecutionFailed(String runId, int turn, AssistantMessage.ToolCall toolCall, Throwable error,
			Duration duration) {
		out.println("  ! " + toolCall.name() + " failed: " + error.getMessage());
	}

	private String summarize(String toolName, String args, String result) {
		return switch (toolName.toLowerCase()) {
			case "write", "write_file" -> toolName + " " + extractPath(args) + " — " + truncate(result, 60);
			case "read_file" -> toolName + " " + extractPath(args);
			case "edit_file" -> toolName + " " + extractPath(args);
			case "bash" -> "bash" + " — " + truncate(result, 80);
			case "glob" -> "glob — " + truncate(result, 80);
			case "grep" -> "grep — " + truncate(result, 80);
			case "submit" -> "done";
			default -> toolName + " — " + truncate(result, 60);
		};
	}

	private String extractPath(String args) {
		// Quick extraction of file path from JSON arguments
		if (args == null) {
			return "";
		}
		for (String key : new String[] { "filePath", "path", "file_path" }) {
			int idx = args.indexOf("\"" + key + "\"");
			if (idx >= 0) {
				int start = args.indexOf("\"", idx + key.length() + 3) + 1;
				int end = args.indexOf("\"", start);
				if (start > 0 && end > start) {
					return args.substring(start, end);
				}
			}
		}
		return "";
	}

	private String truncate(String s, int max) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		s = s.replace("\n", " ").trim();
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}

}
