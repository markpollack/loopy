package io.github.markpollack.loopy.agent;

import io.github.markpollack.loopy.agent.core.LoopState;
import io.github.markpollack.loopy.agent.core.TerminationReason;
import io.github.markpollack.loopy.agent.loop.AgentLoopListener;

import java.io.PrintStream;

/**
 * User-facing verbose loop listener for {@code --debug} mode.
 * <p>
 * Prints turn numbers, token usage, and cost per turn to stderr.
 */
public class DebugLoopListener implements AgentLoopListener {

	private final PrintStream out;

	public DebugLoopListener() {
		this(System.err);
	}

	public DebugLoopListener(PrintStream out) {
		this.out = out;
	}

	@Override
	public void onTurnStarted(String runId, int turn) {
		out.printf("--- Turn %d ---%n", turn);
	}

	@Override
	public void onLoopCompleted(String runId, LoopState state, TerminationReason reason) {
		out.printf("--- Done (%s) — %d turns, %d/%d tokens, $%.4f ---%n", reason.name(), state.currentTurn(),
				state.inputTokensUsed(), state.outputTokensUsed(), state.estimatedCost());
	}

	@Override
	public void onLoopFailed(String runId, LoopState state, Throwable error) {
		out.printf("--- FAILED: %s ---%n", error.getMessage());
	}

}
