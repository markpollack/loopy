package io.github.markpollack.loopy.agent;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MiniAgentToolHandlingTest {

	// --- processToolError ---

	@Test
	void processToolErrorReturnsExceptionMessage() {
		var ex = new RuntimeException("malformed JSON argument");
		assertThat(MiniAgent.processToolError(ex)).isEqualTo("malformed JSON argument");
	}

	@Test
	void processToolErrorFallsBackToCauseClassNameWhenMessageBlank() {
		var cause = new IOException();
		var ex = new RuntimeException("", cause);
		String result = MiniAgent.processToolError(ex);
		assertThat(result).contains("IOException");
	}

	@Test
	void processToolErrorFallsBackToExceptionClassNameWhenNoCause() {
		var ex = new RuntimeException((String) null);
		String result = MiniAgent.processToolError(ex);
		assertThat(result).contains("RuntimeException");
	}

	@Test
	void processToolErrorHandlesIOExceptionSubclass() {
		// MismatchedInputException extends IOException — the original crash scenario
		var ioEx = new IOException("Unexpected token: expected object, got array");
		assertThat(MiniAgent.processToolError(ioEx)).contains("Unexpected token");
	}

	// --- resolveToolCallback ---

	@Test
	void resolveToolCallbackFindsExactLowercaseMatch() {
		var bash = mockCallback();
		var tools = Map.of("bash", bash);
		assertThat(MiniAgent.resolveToolCallback("bash", tools)).isSameAs(bash);
	}

	@Test
	void resolveToolCallbackIsCaseInsensitive() {
		var bash = mockCallback();
		var tools = Map.of("bash", bash);
		assertThat(MiniAgent.resolveToolCallback("Bash", tools)).isSameAs(bash);
		assertThat(MiniAgent.resolveToolCallback("BASH", tools)).isSameAs(bash);
	}

	@Test
	void resolveToolCallbackFallsBackToBashForUnknownTool() {
		var bash = mockCallback();
		var tools = Map.of("bash", bash);
		// "LS" doesn't exist — falls back to bash so loop continues
		assertThat(MiniAgent.resolveToolCallback("LS", tools)).isSameAs(bash);
	}

	@Test
	void resolveToolCallbackReturnsNullWhenBashAlsoMissing() {
		// Edge case: no bash fallback registered
		var tools = Map.<String, ToolCallback>of();
		assertThat(MiniAgent.resolveToolCallback("unknown", tools)).isNull();
	}

	private ToolCallback mockCallback() {
		return mock(ToolCallback.class);
	}

}
