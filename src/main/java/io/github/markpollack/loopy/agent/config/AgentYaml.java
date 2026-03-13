package io.github.markpollack.loopy.agent.config;

import java.util.List;

/**
 * Represents the schema of {@code agent.yaml} — the declarative agent configuration file.
 * Loaded after the Spring context boots (DD-13).
 * <p>
 * Loading order (first file found wins):
 * <ol>
 * <li>{@code ./agent.yaml} — project-local, highest precedence</li>
 * <li>{@code ~/.config/loopy/agent.yaml} — user-global</li>
 * <li>Built-in defaults ({@code dev + boot} profiles active)</li>
 * </ol>
 */
public record AgentYaml(AgentSection agent, ToolsSection tools, RuntimeSection runtime) {

	/** Default config: dev + boot profiles, no MCP, no A2A, no runtime overrides. */
	public static AgentYaml defaults() {
		return new AgentYaml(null, new ToolsSection(List.of("dev", "boot")), null);
	}

	/**
	 * Active tool profiles. Defaults to {@code ["dev", "boot"]} if not declared in the
	 * yaml file.
	 */
	public List<String> activeProfiles() {
		if (tools != null && tools.profiles() != null && !tools.profiles().isEmpty()) {
			return tools.profiles();
		}
		return List.of("dev", "boot");
	}

	/** Optional runtime overrides from {@code agent.yaml} runtime section. */
	public Integer maxTurnsOverride() {
		return runtime != null ? runtime.maxTurns() : null;
	}

	/** Optional model override from {@code agent.yaml} runtime section. */
	public String modelOverride() {
		return runtime != null ? runtime.model() : null;
	}

	public record AgentSection(String name, String description, String version) {
	}

	public record ToolsSection(List<String> profiles) {
	}

	public record RuntimeSection(Integer maxTurns, Double costLimitDollars, String model) {
	}

}
