package io.github.markpollack.loopy.tools;

import io.github.markpollack.loopy.agent.callback.AgentCallback;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Runtime context passed to {@link LoopyToolsFactory} when constructing tool instances.
 * Carries parameters that are only known after CLI flags are parsed — workingDirectory,
 * interactive mode, etc. — making it impossible to build these tools as Spring beans
 * during auto-configuration.
 */
public record ToolFactoryContext(Path workingDirectory, ChatModel chatModel, Duration commandTimeout,
		boolean interactive, @Nullable AgentCallback agentCallback) {

	/** Convenience constructor for non-interactive (batch/headless) contexts. */
	public static ToolFactoryContext headless(Path workingDirectory, ChatModel chatModel, Duration commandTimeout) {
		return new ToolFactoryContext(workingDirectory, chatModel, commandTimeout, false, null);
	}

}
