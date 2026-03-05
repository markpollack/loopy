package io.github.markpollack.loopy.forge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses an experiment brief YAML into structured data.
 *
 * <p>
 * The brief defines the experiment name, package, variants, judges, dataset items, and
 * knowledge files needed to scaffold a complete experiment project.
 * </p>
 *
 * @param name experiment name
 * @param packageName Java package name (e.g., "com.example.experiment.coverage")
 * @param groupId Maven group ID
 * @param artifactId Maven artifact ID
 * @param model default LLM model (null defaults to claude-sonnet-4-6)
 * @param timeoutMinutes per-item timeout in minutes (0 defaults to 15)
 * @param agent agent configuration (description, goal)
 * @param benchmark benchmark configuration (task, dataset items)
 * @param judges judge definitions
 * @param variants variant specifications
 * @param knowledge knowledge file definitions
 */
public record ExperimentBrief(String name, String packageName, String groupId, String artifactId,
		@Nullable String model, int timeoutMinutes, AgentConfig agent, BenchmarkConfig benchmark,
		List<JudgeConfig> judges, List<VariantConfig> variants, KnowledgeConfig knowledge) {

	/**
	 * Parse an experiment brief from a YAML file.
	 */
	@SuppressWarnings("unchecked")
	public static ExperimentBrief parse(Path briefPath) {
		try {
			String yaml = Files.readString(briefPath);
			Yaml yamlParser = new Yaml();
			Map<String, Object> data = yamlParser.load(yaml);

			String name = (String) data.get("name");
			String packageName = (String) data.getOrDefault("package", "com.example.experiment");
			String groupId = (String) data.getOrDefault("groupId", "com.example");
			String artifactId = (String) data.getOrDefault("artifactId", name);
			String model = (String) data.getOrDefault("model", null);
			int timeoutMinutes = data.containsKey("timeoutMinutes") ? ((Number) data.get("timeoutMinutes")).intValue()
					: 0;

			// Parse agent config
			Map<String, Object> agentMap = (Map<String, Object>) data.getOrDefault("agent", Map.of());
			AgentConfig agent = new AgentConfig((String) agentMap.getOrDefault("description", ""),
					(String) agentMap.getOrDefault("goal", ""));

			// Parse benchmark config
			Map<String, Object> benchmarkMap = (Map<String, Object>) data.getOrDefault("benchmark", Map.of());
			List<Map<String, Object>> datasetList = (List<Map<String, Object>>) benchmarkMap.getOrDefault("dataset",
					List.of());
			List<DatasetItemConfig> datasetItems = datasetList.stream()
				.map(d -> new DatasetItemConfig((String) d.get("name"), (String) d.getOrDefault("url", ""),
						(String) d.getOrDefault("subdirectory", ".")))
				.toList();
			BenchmarkConfig benchmark = new BenchmarkConfig((String) benchmarkMap.getOrDefault("task", ""),
					datasetItems);

			// Parse judges
			List<Map<String, Object>> judgesList = (List<Map<String, Object>>) data.getOrDefault("judges", List.of());
			List<JudgeConfig> judges = judgesList.stream()
				.map(j -> new JudgeConfig((String) j.get("name"), ((Number) j.getOrDefault("tier", 0)).intValue(),
						(String) j.getOrDefault("source", "custom"), (String) j.getOrDefault("policy", "FINAL_TIER")))
				.toList();

			// Parse variants
			List<Map<String, Object>> variantsList = (List<Map<String, Object>>) data.getOrDefault("variants",
					List.of());
			List<VariantConfig> variants = variantsList.stream()
				.map(v -> new VariantConfig((String) v.get("name"), (String) v.getOrDefault("prompt", "default.txt"),
						(List<String>) v.getOrDefault("knowledge", List.of())))
				.toList();

			// Parse knowledge config
			Map<String, Object> knowledgeMap = (Map<String, Object>) data.getOrDefault("knowledge", Map.of());
			List<String> knowledgeFiles = (List<String>) knowledgeMap.getOrDefault("files", List.of());
			KnowledgeConfig knowledge = new KnowledgeConfig(knowledgeFiles);

			return new ExperimentBrief(name, packageName, groupId, artifactId, model, timeoutMinutes, agent, benchmark,
					judges, variants, knowledge);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to parse brief: " + briefPath, ex);
		}
	}

	/**
	 * Get the domain name derived from the experiment name (e.g., "Coverage" from
	 * "code-coverage-experiment").
	 */
	public String domainName() {
		String[] parts = name.replace("-experiment", "").split("-");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (!part.isEmpty()) {
				sb.append(Character.toUpperCase(part.charAt(0)));
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}

	public record AgentConfig(String description, String goal) {
	}

	public record BenchmarkConfig(String task, List<DatasetItemConfig> dataset) {
	}

	public record DatasetItemConfig(String name, String url, String subdirectory) {
	}

	public record JudgeConfig(String name, int tier, String source, String policy) {
	}

	public record VariantConfig(String name, String prompt, List<String> knowledge) {
	}

	public record KnowledgeConfig(List<String> files) {
	}

}
