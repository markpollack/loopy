package io.github.markpollack.loopy.agent.journal;

import io.github.markpollack.journal.Run;
import io.github.markpollack.journal.event.LLMCallEvent;
import io.github.markpollack.loopy.agent.core.LoopState;
import io.github.markpollack.loopy.agent.core.TerminationReason;
import io.github.markpollack.loopy.agent.loop.AgentLoopListener;

/**
 * Bridges {@link AgentLoopListener} events to journal-core.
 * <p>
 * Logs an {@link LLMCallEvent} on loop completion with accumulated token/cost metrics,
 * and marks the journal {@link Run} as failed on errors.
 */
public class JournalLoopListener implements AgentLoopListener {

	private final Run run;

	public JournalLoopListener(Run run) {
		this.run = run;
	}

	@Override
	public void onLoopCompleted(String runId, LoopState state, TerminationReason reason) {
		run.logEvent(LLMCallEvent.builder()
			.inputTokens((int) state.inputTokensUsed())
			.outputTokens((int) state.outputTokensUsed())
			.totalCostUsd(state.estimatedCost())
			.build());
		run.setSummary("turns", state.currentTurn());
		run.setSummary("terminationReason", reason.name());
	}

	@Override
	public void onLoopFailed(String runId, LoopState state, Throwable error) {
		run.fail(error);
	}

}
