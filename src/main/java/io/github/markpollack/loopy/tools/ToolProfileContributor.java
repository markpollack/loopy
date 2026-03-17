package io.github.markpollack.loopy.tools;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * SPI for contributing tool callbacks for a named profile (Stage 8b, DD-12).
 * <p>
 * Implement this interface in a JAR and register it via
 * {@code META-INF/services/io.github.markpollack.loopy.tools.ToolProfileContributor}
 * (Java {@link java.util.ServiceLoader}) to make a custom profile available in
 * {@code agent.yaml}.
 * <p>
 * Example {@code agent.yaml} using a custom contributor profile:
 *
 * <pre>
 * tools:
 *   profiles:
 *     - dev
 *     - my-db-tools
 * </pre>
 *
 * Loopy will call {@link #tools(ToolFactoryContext)} when the named profile is active and
 * include the returned callbacks in the agent's tool list.
 */
public interface ToolProfileContributor {

	/** The profile name as it appears in {@code agent.yaml tools.profiles}. */
	String profileName();

	/**
	 * Returns the tool callbacks for this profile.
	 * @param ctx runtime context providing working directory, chat model, and timeout
	 */
	List<ToolCallback> tools(ToolFactoryContext ctx);

}
