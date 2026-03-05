package io.github.markpollack.loopy.forge;

import java.util.stream.Collectors;

/**
 * Builds the LLM prompt that instructs the agent to generate domain-aware content for the
 * scaffolded experiment project.
 *
 * <p>
 * This runs AFTER deterministic customization (TemplateCustomizer) has already handled
 * package refactoring, pom.xml updates, and file generation. The LLM pass generates
 * creative content that benefits from domain understanding: starter prompt content, judge
 * stub implementations, VISION.md, DESIGN.md.
 * </p>
 */
public class CustomizationPromptBuilder {

	/**
	 * Build the LLM customization prompt from the experiment brief.
	 *
	 * <p>
	 * The generated prompt instructs the agent to create domain-aware content in the
	 * already-scaffolded project directory.
	 * </p>
	 */
	public String build(ExperimentBrief brief, String projectDir) {
		String domain = brief.domainName();

		StringBuilder sb = new StringBuilder();
		sb.append("You are customizing an agent experiment project that has already been scaffolded.\n");
		sb.append("The project is at: ").append(projectDir).append("\n\n");

		sb.append("## Project Context\n\n");
		sb.append("- Experiment name: ").append(brief.name()).append("\n");
		sb.append("- Domain: ").append(domain).append("\n");
		sb.append("- Agent description: ").append(brief.agent().description()).append("\n");
		sb.append("- Agent goal: ").append(brief.agent().goal()).append("\n");
		sb.append("- Benchmark task: ").append(brief.benchmark().task()).append("\n");
		sb.append("- Dataset items: ").append(brief.benchmark().dataset().size()).append("\n");
		sb.append("- Variants: ").append(brief.variants().size()).append("\n\n");

		sb.append("## What's Already Done (DO NOT repeat)\n\n");
		sb.append("- Package refactoring (").append(brief.packageName()).append(")\n");
		sb.append("- pom.xml GAV updates\n");
		sb.append("- AgentInvoker renamed to ").append(domain).append("AgentInvoker\n");
		sb.append("- dataset/items.yaml generated\n");
		sb.append("- experiment-config.yaml generated\n");
		sb.append("- Prompt placeholder files created\n");
		sb.append("- Knowledge index.md and placeholder files created\n\n");

		sb.append("## Your Tasks — Generate Domain-Aware Content\n\n");

		// Task 1: Starter prompts
		sb.append("### 1. Write Starter Prompts\n\n");
		sb.append("Update the prompt files in `prompts/` with domain-specific content.\n\n");

		String promptFiles = brief.variants().isEmpty() ? "v0-naive.txt, v1-hardened.txt, v2-with-kb.txt"
				: brief.variants()
					.stream()
					.map(ExperimentBrief.VariantConfig::prompt)
					.distinct()
					.collect(Collectors.joining(", "));
		sb.append("Files: ").append(promptFiles).append("\n\n");

		sb.append("For the naive/control prompt: Keep it minimal — just the task description.\n");
		sb.append("For the hardened prompt: Add structured execution steps, domain-specific constraints, ");
		sb.append("verification commands, and examples of good vs bad output.\n");
		sb.append("For the KB-aware prompt: Same as hardened PLUS instructions to read knowledge/ files first.\n\n");

		sb.append("Use the agent description and goal to make prompts domain-specific:\n");
		sb.append("- Description: ").append(brief.agent().description()).append("\n");
		sb.append("- Goal: ").append(brief.agent().goal()).append("\n");
		sb.append("- Task: ").append(brief.benchmark().task()).append("\n\n");

		// Task 2: Judge stubs
		if (!brief.judges().isEmpty()) {
			sb.append("### 2. Generate Judge Stubs\n\n");
			for (ExperimentBrief.JudgeConfig judge : brief.judges()) {
				if ("custom".equals(judge.source())) {
					sb.append("Create `").append(judge.name()).append(".java` in the package's judges subpackage.\n");
					sb.append("  - Tier: ").append(judge.tier()).append("\n");
					sb.append("  - Policy: ").append(judge.policy()).append("\n");
					sb.append("  - Extend DeterministicJudge from agent-judge-core\n");
					sb.append("  - Add meaningful evaluation criteria comments based on the domain\n");
					sb.append("  - The judge should evaluate: ").append(brief.agent().goal()).append("\n\n");
				}
			}
		}

		// Task 3: VISION.md
		sb.append("### 3. Fill VISION.md\n\n");
		sb.append("Update `plans/VISION.md` with:\n");
		sb.append("- **Problem Statement**: derived from agent description: ")
			.append(brief.agent().description())
			.append("\n");
		sb.append("- **Hypothesis**: what you expect knowledge + structured execution to improve\n");
		sb.append("- **Success Criteria**: measurable goals based on: ").append(brief.agent().goal()).append("\n");
		sb.append("- **Scope**: what's in and out of scope for this experiment\n\n");

		// Task 4: DESIGN.md
		sb.append("### 4. Fill DESIGN.md\n\n");
		sb.append("Update `plans/DESIGN.md` with:\n");
		sb.append("- Judge tier table populated with actual judges\n");
		sb.append("- Variant ablation table with rationale for each variant\n");
		sb.append("- Dataset description\n");
		sb.append("- Knowledge strategy description\n\n");

		// Constraints
		sb.append("## Important\n\n");
		sb.append(
				"- Do NOT modify ExperimentApp.java, JuryFactory.java, GrowthStoryReporter.java, or other pre-wired classes\n");
		sb.append("- Do NOT modify pom.xml, experiment-config.yaml, or dataset/items.yaml\n");
		sb.append("- Write content that is specific to the domain (")
			.append(domain)
			.append("), not generic boilerplate\n");
		sb.append("- All generated Java code should compile\n");

		return sb.toString();
	}

}
