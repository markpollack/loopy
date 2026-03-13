package io.github.markpollack.loopy.agent.config;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads and parses {@code agent.yaml} after the Spring context has started (DD-13).
 * <p>
 * No pre-boot property setting needed — profiles are resolved at MiniAgent construction
 * time, not during auto-configuration (DD-12, DD-16).
 */
public class AgentYamlLoader {

	private static final Logger log = LoggerFactory.getLogger(AgentYamlLoader.class);

	/**
	 * Loads agent configuration for the given project directory. Tries project-local,
	 * then user-global, then falls back to built-in defaults.
	 */
	public static AgentYaml load(Path projectDir) {
		Path projectYaml = projectDir.resolve("agent.yaml");
		if (Files.isRegularFile(projectYaml)) {
			log.debug("Loading agent.yaml from project: {}", projectYaml);
			AgentYaml loaded = parse(projectYaml);
			if (loaded != null) {
				return loaded;
			}
		}

		Path globalYaml = Path.of(System.getProperty("user.home"), ".config", "loopy", "agent.yaml");
		if (Files.isRegularFile(globalYaml)) {
			log.debug("Loading agent.yaml from global: {}", globalYaml);
			AgentYaml loaded = parse(globalYaml);
			if (loaded != null) {
				return loaded;
			}
		}

		log.debug("No agent.yaml found; using defaults (dev + boot profiles active)");
		return AgentYaml.defaults();
	}

	@SuppressWarnings("unchecked")
	private static @Nullable AgentYaml parse(Path path) {
		try {
			String content = Files.readString(path);
			Yaml yaml = new Yaml();
			Map<String, Object> root = yaml.load(content);
			if (root == null) {
				return AgentYaml.defaults();
			}

			// Parse agent section
			AgentYaml.AgentSection agentSection = null;
			if (root.get("agent") instanceof Map<?, ?> agentMap) {
				agentSection = new AgentYaml.AgentSection((String) agentMap.get("name"),
						(String) agentMap.get("description"), (String) agentMap.get("version"));
			}

			// Parse tools.profiles
			AgentYaml.ToolsSection toolsSection = null;
			if (root.get("tools") instanceof Map<?, ?> toolsMap) {
				if (toolsMap.get("profiles") instanceof List<?> profilesList) {
					toolsSection = new AgentYaml.ToolsSection(List.copyOf((List<String>) profilesList));
				}
			}

			// Parse runtime section
			AgentYaml.RuntimeSection runtimeSection = null;
			if (root.get("runtime") instanceof Map<?, ?> runtimeMap) {
				Integer maxTurns = runtimeMap.get("max-turns") instanceof Number n ? n.intValue() : null;
				Double costLimit = runtimeMap.get("cost-limit-dollars") instanceof Number n ? n.doubleValue() : null;
				String model = (String) runtimeMap.get("model");
				runtimeSection = new AgentYaml.RuntimeSection(maxTurns, costLimit, model);
			}

			return new AgentYaml(agentSection, toolsSection, runtimeSection);
		}
		catch (Exception ex) {
			log.warn("Failed to parse agent.yaml at {} — using defaults. Cause: {}", path, ex.getMessage());
			return null;
		}
	}

}
