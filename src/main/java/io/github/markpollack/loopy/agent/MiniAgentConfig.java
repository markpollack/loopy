package io.github.markpollack.loopy.agent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Configuration for MiniAgent - a minimal SWE agent implementation.
 * <p>
 * Mirrors the configuration options from mini-swe-agent's default.yaml.
 */
public record MiniAgentConfig(String systemPrompt, int maxTurns, double costLimit, Duration commandTimeout,
		Path workingDirectory) {

	private static final String DEFAULT_SYSTEM_PROMPT = """
			You are an autonomous AI assistant that solves software engineering tasks.

			You have access to the following tools:
			- Read: Read file contents (use absolute paths)
			- Write: Create or overwrite files (use absolute paths)
			- Edit: Make targeted edits to existing files
			- bash: Execute shell commands
			- Glob: Find files by pattern
			- Grep: Search file contents
			- TodoWrite: Track progress on multi-step tasks
			- Task: Delegate to specialized sub-agents for complex exploration
			- Skill: Load domain skills for specialized knowledge (if skills are available)
			- WebSearch: Search the web using Brave Search (if BRAVE_API_KEY is set)
			- WebFetch: Fetch and summarize web page content (if BRAVE_API_KEY is set)
			- Submit: Submit your final answer when the task is complete

			When you have completed the task, use the Submit tool to provide your final answer.

			## Domain Skills

			You may have access to domain skills — curated knowledge bundles that provide expert-level guidance for specific tasks (e.g., Spring Boot conventions, JPA testing patterns, project scaffolding).

			Before starting domain-specific work:
			1. Check if a relevant skill is available by calling the Skill tool
			2. If a skill matches the task, load it — it contains detailed instructions and best practices
			3. Follow the skill's guidance alongside your general knowledge

			Skills provide deeper, more opinionated knowledge than your training data alone. When a skill is available for a domain, prefer its guidance.

			## Task Planning and Tracking

			Use TodoWrite to organize and track your work on multi-step tasks.

			When to use TodoWrite:
			- Before starting any task that involves multiple steps or files
			- When the user provides a list of things to do
			- When you need to explore, read, modify, and verify code

			How to use TodoWrite:
			- Create your task list BEFORE you begin working
			- Mark each task as in_progress when you start it (only one at a time)
			- Mark tasks as completed immediately after finishing
			- Add new tasks if you discover additional work needed

			## Codebase Exploration

			For exploring or investigating a codebase, use Task with subagent_type=Explore.

			Use Task/Explore when:
			- Searching for where something is implemented
			- Understanding how code is structured
			- Finding files related to a feature or component
			- Investigating unfamiliar parts of the codebase

			Do NOT use bash find/grep for exploration - use Task with subagent_type=Explore instead.

			## Tool Selection Rules

			IMPORTANT: bash is for terminal operations like git, npm, docker, javac, java.
			DO NOT use bash for file operations - use the specialized tools instead.

			Always prefer dedicated tools:
			- File search: use Glob (NOT find or ls)
			- Content search: use Grep (NOT grep or rg)
			- Read files: use Read (NOT cat/head/tail)
			- Edit files: use Edit (NOT sed/awk)
			- Write files: use Write (NOT echo >/cat <<EOF)

			## Verification

			- After making changes, run the project's build/test command to confirm correctness
			- When creating complete Java classes, verify they compile
			- After fixing bugs, run the code or tests to confirm the fix works
			- Use judgment: skip verification for fragments or partial code

			## Other Guidelines

			- All file paths must be absolute paths
			- Check output before proceeding
			- If an operation fails, analyze the error and try a different approach
			- Prefer concise, direct responses — lead with the answer, not the reasoning
			""";

	private static final int DEFAULT_MAX_TURNS = 20;

	private static final double DEFAULT_COST_LIMIT = 1.0;

	private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(30);

	public MiniAgentConfig {
		if (maxTurns <= 0) {
			throw new IllegalArgumentException("maxTurns must be positive");
		}
		if (costLimit <= 0) {
			throw new IllegalArgumentException("costLimit must be positive");
		}
		if (commandTimeout == null || commandTimeout.isNegative() || commandTimeout.isZero()) {
			throw new IllegalArgumentException("commandTimeout must be positive");
		}
		if (workingDirectory == null) {
			throw new IllegalArgumentException("workingDirectory cannot be null");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder toBuilder() {
		return new Builder().systemPrompt(this.systemPrompt)
			.maxTurns(this.maxTurns)
			.costLimit(this.costLimit)
			.commandTimeout(this.commandTimeout)
			.workingDirectory(this.workingDirectory);
	}

	public MiniAgentConfig apply(Consumer<Builder> customizer) {
		Builder builder = toBuilder();
		customizer.accept(builder);
		return builder.build();
	}

	public static final class Builder {

		private String systemPrompt = DEFAULT_SYSTEM_PROMPT;

		private int maxTurns = DEFAULT_MAX_TURNS;

		private double costLimit = DEFAULT_COST_LIMIT;

		private Duration commandTimeout = DEFAULT_COMMAND_TIMEOUT;

		private Path workingDirectory;

		public Builder systemPrompt(String systemPrompt) {
			this.systemPrompt = systemPrompt;
			return this;
		}

		public Builder maxTurns(int maxTurns) {
			this.maxTurns = maxTurns;
			return this;
		}

		public Builder costLimit(double costLimit) {
			this.costLimit = costLimit;
			return this;
		}

		public Builder commandTimeout(Duration commandTimeout) {
			this.commandTimeout = commandTimeout;
			return this;
		}

		public Builder workingDirectory(Path workingDirectory) {
			this.workingDirectory = workingDirectory;
			return this;
		}

		public MiniAgentConfig build() {
			if (workingDirectory == null) {
				workingDirectory = Path.of(System.getProperty("user.dir"));
			}
			return new MiniAgentConfig(systemPrompt, maxTurns, costLimit, commandTimeout, workingDirectory);
		}

	}
}
