package io.github.markpollack.loopy.agent.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Immutable state of an agent loop at a point in time.
 * <p>
 * Tracks turn history and metrics for termination strategy evaluation. Does not store the
 * actual conversation messages - use Spring AI's ChatMemory or the loop's internal
 * message list for that.
 */
public record LoopState(String runId, int currentTurn, Instant startedAt, long totalTokensUsed, long inputTokensUsed,
		long outputTokensUsed, double estimatedCost, boolean abortSignalled, List<TurnSnapshot> turnHistory,
		int consecutiveSameOutputCount, int consecutiveIdenticalToolCallCount, int compactionCount) {

	/**
	 * Creates an initial loop state.
	 */
	public static LoopState initial(String runId) {
		return new LoopState(runId, 0, Instant.now(), 0L, 0L, 0L, 0.0, false, List.of(), 0, 0, 0);
	}

	/**
	 * Returns a new state after completing a turn.
	 * @param inputTokens input tokens used in this turn
	 * @param outputTokens output tokens used in this turn
	 * @param cost estimated cost of this turn
	 * @param hasToolCalls whether this turn had tool calls
	 * @param outputSignature hash of output for stuck detection
	 * @param toolCallHash combined SHA-256 hash of all tool calls in this turn, or null
	 * if no tools were called
	 */
	public LoopState completeTurn(long inputTokens, long outputTokens, double cost, boolean hasToolCalls,
			int outputSignature, String toolCallHash) {
		long tokensUsed = inputTokens + outputTokens;
		var newHistory = new ArrayList<>(turnHistory);
		var snapshot = new TurnSnapshot(currentTurn, tokensUsed, inputTokens, outputTokens, cost, hasToolCalls,
				outputSignature, toolCallHash);
		newHistory.add(snapshot);

		int sameCount = calculateSameOutputCount(newHistory, outputSignature);
		int identicalToolCallCount = calculateConsecutiveIdenticalToolCalls(newHistory);

		return new LoopState(runId, currentTurn + 1, startedAt, totalTokensUsed + tokensUsed,
				inputTokensUsed + inputTokens, outputTokensUsed + outputTokens, estimatedCost + cost, abortSignalled,
				List.copyOf(newHistory), sameCount, identicalToolCallCount, compactionCount);
	}

	/**
	 * Returns a new state with abort signalled.
	 */
	public LoopState abort() {
		return new LoopState(runId, currentTurn, startedAt, totalTokensUsed, inputTokensUsed, outputTokensUsed,
				estimatedCost, true, turnHistory, consecutiveSameOutputCount, consecutiveIdenticalToolCallCount,
				compactionCount);
	}

	/**
	 * Returns a new state with compaction count incremented.
	 */
	public LoopState compacted() {
		return new LoopState(runId, currentTurn, startedAt, totalTokensUsed, inputTokensUsed, outputTokensUsed,
				estimatedCost, abortSignalled, turnHistory, consecutiveSameOutputCount,
				consecutiveIdenticalToolCallCount, compactionCount + 1);
	}

	/**
	 * Returns the elapsed time since the loop started.
	 */
	public Duration elapsed() {
		return Duration.between(startedAt, Instant.now());
	}

	/**
	 * Returns the last turn snapshot if available.
	 */
	public Optional<TurnSnapshot> lastTurn() {
		return turnHistory.isEmpty() ? Optional.empty() : Optional.of(turnHistory.get(turnHistory.size() - 1));
	}

	/**
	 * Returns true if stuck (same output repeated threshold times).
	 */
	public boolean isStuck(int threshold) {
		return consecutiveSameOutputCount >= threshold;
	}

	/**
	 * Returns true if the same tool call set has been repeated consecutively at least
	 * {@code threshold} times.
	 */
	public boolean isToolCallStuck(int threshold) {
		return consecutiveIdenticalToolCallCount >= threshold;
	}

	/**
	 * Returns true if tool calls are alternating in an A-B-A-B pattern across the last
	 * {@code windowSize} turns that had tool calls.
	 */
	public boolean isAlternatingToolCalls(int windowSize) {
		var toolCallTurns = turnHistory.stream().filter(t -> t.toolCallHash() != null).toList();
		if (toolCallTurns.size() < windowSize) {
			return false;
		}
		var window = toolCallTurns.subList(toolCallTurns.size() - windowSize, toolCallTurns.size());
		// Exactly 2 distinct hashes, strictly alternating
		var hashes = window.stream().map(TurnSnapshot::toolCallHash).toList();
		String first = hashes.get(0);
		String second = null;
		for (int i = 1; i < hashes.size(); i++) {
			String h = hashes.get(i);
			if (second == null && !h.equals(first)) {
				second = h;
			}
			String expected = (i % 2 == 0) ? first : second;
			if (expected == null || !h.equals(expected)) {
				return false;
			}
		}
		return second != null; // must have found 2 distinct hashes
	}

	/**
	 * Returns true if the cost limit has been exceeded.
	 */
	public boolean costExceeded(double limit) {
		return limit > 0 && estimatedCost > limit;
	}

	/**
	 * Returns true if timeout has been exceeded.
	 */
	public boolean timeoutExceeded(Duration timeout) {
		return elapsed().compareTo(timeout) > 0;
	}

	/**
	 * Returns true if max turns reached.
	 */
	public boolean maxTurnsReached(int maxTurns) {
		return currentTurn >= maxTurns;
	}

	private int calculateConsecutiveIdenticalToolCalls(List<TurnSnapshot> history) {
		if (history.isEmpty()) {
			return 0;
		}
		TurnSnapshot latest = history.get(history.size() - 1);
		if (latest.toolCallHash() == null) {
			return 0;
		}
		int count = 1;
		for (int i = history.size() - 2; i >= 0; i--) {
			TurnSnapshot t = history.get(i);
			if (!latest.toolCallHash().equals(t.toolCallHash())) {
				break;
			}
			count++;
		}
		return count;
	}

	private int calculateSameOutputCount(List<TurnSnapshot> history, int currentSignature) {
		// Get the most recent turn (which was just added)
		TurnSnapshot currentTurn = history.get(history.size() - 1);

		// If this turn had tool calls, agent is making progress - not stuck
		if (currentTurn.hadToolCalls()) {
			return 0;
		}

		// Count consecutive turns with same signature AND no tool calls
		int count = 1;
		for (int i = history.size() - 2; i >= 0; i--) {
			TurnSnapshot turn = history.get(i);
			// Stop counting if: different signature OR had tool calls (making progress)
			if (turn.outputSignature() != currentSignature || turn.hadToolCalls()) {
				break;
			}
			count++;
		}
		return count;
	}

	/**
	 * Snapshot of a single turn for history tracking.
	 */
	public record TurnSnapshot(int turn, long tokensUsed, long inputTokens, long outputTokens, double cost,
			boolean hadToolCalls, int outputSignature, String toolCallHash) {
	}
}
