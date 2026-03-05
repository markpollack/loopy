package io.github.markpollack.loopy.agent.core;

/**
 * Reason for loop termination.
 */
public enum TerminationReason {

	/** Loop has not terminated yet */
	NOT_TERMINATED,
	/** Score threshold was met */
	SCORE_THRESHOLD_MET,
	/** Maximum iterations/trials reached */
	MAX_ITERATIONS_REACHED,
	/** Maximum turns reached */
	MAX_TURNS_REACHED,
	/** Agent detected as stuck (no progress) */
	STUCK_DETECTED,
	/** User approved the result */
	USER_APPROVAL,
	/** Finish/submit tool was called */
	FINISH_TOOL_CALLED,
	/** Terminal state reached (state machine) */
	STATE_TERMINAL,
	/** Workflow completed all steps */
	WORKFLOW_COMPLETE,
	/** Timeout exceeded */
	TIMEOUT,
	/** Cost limit exceeded */
	COST_LIMIT_EXCEEDED,
	/** External abort signal received */
	EXTERNAL_SIGNAL,
	/** Error occurred during execution */
	ERROR

}
