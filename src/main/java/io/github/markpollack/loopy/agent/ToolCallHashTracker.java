package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.ToolCallListener;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

/**
 * Tracks tool call hashes per turn for stuck detection.
 * <p>
 * On each tool execution start, computes SHA-256 of {@code toolName:arguments} and
 * accumulates hashes for the current turn. Call {@link #getAndResetTurnHash()} after each
 * turn completes to retrieve the combined hash and reset for the next turn.
 * <p>
 * The combined hash is SHA-256 of all per-call hashes joined with {@code ","}, preserving
 * call order. Two turns with identical tool calls in the same order will produce the same
 * hash.
 */
public class ToolCallHashTracker implements ToolCallListener {

	private final ThreadLocal<ArrayList<String>> pendingHashes = ThreadLocal.withInitial(ArrayList::new);

	@Override
	public void onToolExecutionStarted(String runId, int turn, AssistantMessage.ToolCall toolCall) {
		String input = toolCall.name() + ":" + (toolCall.arguments() != null ? toolCall.arguments() : "");
		pendingHashes.get().add(sha256(input));
	}

	/**
	 * Returns the combined SHA-256 hash of all tool calls accumulated since the last
	 * reset, then clears the accumulator. Returns {@code null} if no tool calls occurred.
	 */
	public String getAndResetTurnHash() {
		var hashes = pendingHashes.get();
		if (hashes.isEmpty()) {
			return null;
		}
		String combined = String.join(",", hashes);
		hashes.clear();
		return sha256(combined);
	}

	static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			var sb = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is guaranteed by the JVM spec
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

}
