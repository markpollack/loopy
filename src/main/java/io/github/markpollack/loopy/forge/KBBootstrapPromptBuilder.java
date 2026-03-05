package io.github.markpollack.loopy.forge;

/**
 * Builds the system prompt for the KB scouting agent that populates a scaffolded
 * project's {@code knowledge/} directory with real content from web references.
 *
 * <p>
 * The prompt instructs the agent to search for A-tier official docs using Brave Search,
 * fetch and summarize them, then synthesize into opinionated KB files with a routing
 * index.
 * </p>
 *
 * @see ForgeAgentCommand
 */
public class KBBootstrapPromptBuilder {

	/**
	 * Build the system prompt for KB bootstrapping.
	 * @param brief the experiment brief providing domain context
	 * @param knowledgeDir absolute path to the knowledge directory
	 * @return system prompt for the KB scouting agent
	 */
	public String buildSystemPrompt(ExperimentBrief brief, String knowledgeDir) {
		String domain = brief.domainName();
		String agentDescription = brief.agent().description();
		String agentGoal = brief.agent().goal();
		String task = brief.benchmark().task();

		StringBuilder sb = new StringBuilder();
		sb.append("You are a reference harvester bootstrapping a knowledge base for an agent experiment.\n\n");

		sb.append("## Context\n\n");
		sb.append("- Domain: ").append(domain).append("\n");
		sb.append("- Agent description: ").append(agentDescription).append("\n");
		sb.append("- Agent goal: ").append(agentGoal).append("\n");
		sb.append("- Benchmark task: ").append(task).append("\n");
		sb.append("- Knowledge directory: ").append(knowledgeDir).append("\n\n");

		sb.append("## Your Mission\n\n");
		sb.append("Search for and fetch A-tier official documentation relevant to this domain, ");
		sb.append("then synthesize it into opinionated knowledge base files that an agent can use.\n\n");

		sb.append("## Process\n\n");
		sb.append("1. **Search** — Use web_search to find A-tier official documentation:\n");
		sb.append("   - Official framework/library docs (e.g., spring.io, docs.oracle.com)\n");
		sb.append("   - Official API references and guides\n");
		sb.append("   - Authoritative tutorials from the project maintainers\n");
		sb.append("   - Focus on sources directly relevant to: ").append(task).append("\n\n");

		sb.append("2. **Fetch** — Use smart_web_fetch to retrieve the most valuable pages:\n");
		sb.append("   - Prioritize official docs over community tutorials\n");
		sb.append("   - Fetch 3-5 high-value pages (quality over quantity)\n");
		sb.append("   - Skip pages that are just link indexes or navigation pages\n\n");

		sb.append("3. **Synthesize** — Write opinionated KB files (NOT raw dumps):\n");
		sb.append("   - Extract actionable patterns, best practices, and concrete examples\n");
		sb.append("   - Organize by topic, not by source\n");
		sb.append("   - Include code snippets where they illustrate key patterns\n");
		sb.append("   - Write in a style that helps an agent accomplish the benchmark task\n\n");

		sb.append("4. **Write Files** — Save KB files to the knowledge directory:\n");
		sb.append("   - Create files in ").append(knowledgeDir).append("/domain/\n");
		sb.append("   - Use meaningful filenames (e.g., testing-patterns.md, api-reference.md)\n");
		sb.append("   - Each file should be self-contained and focused on one topic\n\n");

		sb.append("5. **Update Index** — Update ").append(knowledgeDir).append("/index.md:\n");
		sb.append("   - Add entries for each KB file you created\n");
		sb.append("   - Use the format: `| domain/<filename>.md | Read when <description> |`\n");
		sb.append("   - The \"read when\" description should tell an agent WHEN this file is useful\n\n");

		sb.append("## File Format\n\n");
		sb.append("Each KB file should follow this structure:\n\n");
		sb.append("```\n");
		sb.append("# <Topic Title>\n\n");
		sb.append("> One-line summary of what this file covers and when to read it.\n\n");
		sb.append("## Key Concepts\n");
		sb.append("- Bullet points of the most important things to know\n\n");
		sb.append("## Patterns\n");
		sb.append("Concrete code examples and recommended approaches.\n\n");
		sb.append("## Common Pitfalls\n");
		sb.append("What to avoid and why.\n\n");
		sb.append("## References\n");
		sb.append("- Source URLs for attribution\n");
		sb.append("```\n\n");

		sb.append("## Important\n\n");
		sb.append("- Write 2-4 KB files — enough to be useful, not overwhelming\n");
		sb.append("- Be opinionated: recommend specific approaches, don't just list options\n");
		sb.append("- Focus on content relevant to the benchmark task: ").append(task).append("\n");
		sb.append("- All file paths must be absolute\n");
		sb.append("- Create the domain/ subdirectory if it doesn't exist\n");

		return sb.toString();
	}

	/**
	 * Build the user message that kicks off the KB scouting agent.
	 * @param brief the experiment brief
	 * @return user message for the KB scouting agent
	 */
	public String buildUserMessage(ExperimentBrief brief) {
		return "Bootstrap the knowledge base for the \"" + brief.name() + "\" experiment. " + "The agent needs to "
				+ brief.agent().goal() + " by " + brief.benchmark().task() + ". "
				+ "Search for official documentation, fetch the best sources, and synthesize into KB files.";
	}

}
