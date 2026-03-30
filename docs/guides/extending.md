# Extending Loopy

Loopy has five extension points, from zero-code (drop a Markdown file) to full Java SPI (ship a JAR). Each one makes the agent smarter or more capable in your domain.

| Extension | What it does | Effort | Ship as |
|-----------|-------------|--------|---------|
| [Skills](#skills) | Domain knowledge the agent reads on demand | A Markdown file | File or JAR |
| [Subagents](#subagents) | Specialist agents the main agent delegates to | A Markdown file | File |
| [Tool Profiles](#tool-profiles) | New tools the agent can call | Java + SPI | JAR |
| [Listeners](#listeners) | Observe tool calls and loop events | Java | Embedded |
| [Programmatic API](#programmatic-api) | Embed Loopy's agent in your app | Java | Dependency |

---

## Skills

Skills are the fastest way to make Loopy smarter. A skill is a Markdown file with YAML frontmatter. The agent sees skill names and descriptions upfront but only loads the full content when it's relevant to the task — no tokens wasted.

### Create a skill

Create `.claude/skills/my-conventions/SKILL.md` in your project:

```markdown
---
name: my-conventions
description: Team coding conventions for our Spring Boot services
---

# Instructions

When working on this codebase:
- Use constructor injection, never field injection
- All REST endpoints return ProblemDetail for errors (RFC 9457)
- Tests use @WebMvcTest with MockMvc, not @SpringBootTest
- Entity IDs are UUIDs, never auto-increment
```

That's it. Next time you run Loopy in that project directory, the agent sees the skill and uses it when relevant.

### Where skills live

Loopy discovers skills from three locations, checked in order:

| Location | Path | Use case |
|----------|------|----------|
| Project | `.claude/skills/*/SKILL.md` | Team conventions, checked into the repo |
| Global | `~/.claude/skills/*/SKILL.md` | Personal skills across all projects |
| Classpath | `META-INF/skills/*/SKILL.md` in JARs | Published skill packages (Maven dependency) |

### Install from the catalog

Loopy ships with a curated catalog of 23+ skills from 8 publishers:

```bash
/skills search testing          # Browse by keyword
/skills info systematic-debugging  # See details
/skills add systematic-debugging   # Install to ~/.claude/skills/
/skills remove systematic-debugging
```

### Publish skills as a JAR (SkillsJars)

Package skills as a Maven dependency so teams get them automatically:

```
my-skills.jar
└── META-INF/skills/
    └── my-org/my-repo/api-design/
        └── SKILL.md
```

Add the JAR to a project's `pom.xml` and Loopy discovers it on the classpath. The directory structure under `META-INF/skills/` follows the [agentskills.io](https://agentskills.io) convention: `{publisher}/{repo}/{skill-name}/SKILL.md`.

Skills follow the agentskills.io spec and work in 40+ agentic CLIs — not just Loopy.

---

## Subagents

Subagents are specialist agents the main agent delegates to via the `Task` tool. Define them as Markdown files — no Java required.

### Create a subagent

Create `.claude/agents/test-runner.md` in your project:

```markdown
---
name: test-runner
description: Runs tests and reports pass/fail summary
tools: Bash, Read
---

You are a testing specialist. When invoked:

1. Run `./mvnw test` in the working directory
2. Parse the output for pass/fail/skip counts
3. If tests fail, read the relevant test source files
4. Report a concise summary: what passed, what failed, and why
```

The main agent automatically delegates testing tasks to this subagent based on the `description` field.

### Frontmatter fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier (lowercase, hyphens) |
| `description` | Yes | When to use this agent — the main agent reads this to decide |
| `tools` | No | Allowed tools (comma-separated). Inherits all tools if omitted |
| `disallowedTools` | No | Explicitly denied tools |
| `model` | No | `haiku`, `sonnet`, or `opus`. Defaults to parent model |

### Where subagents live

| Location | Path |
|----------|------|
| Project | `.claude/agents/*.md` |
| Global | `~/.claude/agents/*.md` |

### Tips

- Keep the `description` specific — the main agent uses it for routing. "Runs tests" is better than "helps with testing."
- Restrict `tools` to what the subagent needs. A test runner doesn't need `Edit` or `Write`.
- Subagents run in isolated context windows. They can't see the main conversation history.
- Subagents cannot spawn other subagents (the `Task` tool is excluded automatically).

See [Subagents](../subagents.md) for built-in subagents (Explore, Plan, General-Purpose, Bash) and advanced patterns.

---

## Tool Profiles

Tool profiles add new tools the agent can call. This is a Java SPI — you implement an interface, package it as a JAR, and Loopy discovers it at startup via `ServiceLoader`.

### Implement ToolProfileContributor

```java
package com.example.tools;

import io.github.markpollack.loopy.tools.ToolProfileContributor;
import io.github.markpollack.loopy.tools.ToolFactoryContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;

import java.util.List;

public class DatabaseToolProfile implements ToolProfileContributor {

    @Override
    public String profileName() {
        return "database-tools";
    }

    @Override
    public List<ToolCallback> tools(ToolFactoryContext ctx) {
        return List.of(
            ToolCallbacks.from(new QueryTool(ctx.workingDirectory())),
            ToolCallbacks.from(new SchemaTool(ctx.workingDirectory()))
        );
    }
}
```

### Register via ServiceLoader

Create `META-INF/services/io.github.markpollack.loopy.tools.ToolProfileContributor`:

```
com.example.tools.DatabaseToolProfile
```

### What ToolFactoryContext provides

| Field | Type | Description |
|-------|------|-------------|
| `workingDirectory` | `Path` | Agent's working directory |
| `chatModel` | `ChatModel` | The active LLM (for tools that need AI) |
| `commandTimeout` | `Duration` | Tool execution timeout (default 120s) |
| `interactive` | `boolean` | True in TUI mode, false in print/REPL |
| `agentCallback` | `AgentCallback` | TUI callbacks (null in non-TUI modes) |

### Built-in profiles

| Profile | Tools |
|---------|-------|
| `dev` | Full interactive toolset (bash, file I/O, search, skills, subagents) |
| `boot` | Spring Boot scaffolding tools |
| `headless` | Same as `dev` minus `AskUserQuestion` (for CI/CD) |
| `readonly` | Read-only: file read, grep, glob, list directory |

Custom profiles are loaded **alongside** built-in profiles, not replacing them.

---

## Listeners

Listeners let you observe what the agent does without changing its behavior. Two interfaces:

### ToolCallListener

Fires around every tool execution:

```java
public class CostTracker implements ToolCallListener {

    @Override
    public void onToolExecutionCompleted(String runId, int turn,
            AssistantMessage.ToolCall toolCall, String result, Duration duration) {
        log.info("Tool {} took {}ms", toolCall.name(), duration.toMillis());
    }

    @Override
    public void onToolExecutionFailed(String runId, int turn,
            AssistantMessage.ToolCall toolCall, Throwable error, Duration duration) {
        log.warn("Tool {} failed: {}", toolCall.name(), error.getMessage());
    }
}
```

### AgentLoopListener

Fires at loop lifecycle boundaries:

```java
public class ProgressReporter implements AgentLoopListener {

    @Override
    public void onTurnCompleted(String runId, int turn, TerminationReason reason) {
        System.err.printf("Turn %d complete%n", turn);
    }

    @Override
    public void onLoopCompleted(String runId, LoopState state, TerminationReason reason) {
        System.err.printf("Agent finished: %s (%d turns)%n", reason, state.turns());
    }
}
```

### Wiring listeners

Listeners are wired through the `MiniAgent` builder (used internally by `LoopyAgent`):

```java
var agent = MiniAgent.builder()
    .config(config)
    .model(chatModel)
    .toolCallListener(new CostTracker())
    .loopListener(new ProgressReporter())
    .build();
```

Listener methods should not throw exceptions — exceptions are logged and swallowed to avoid crashing the agent loop.

---

## Programmatic API

Embed Loopy's agent in other Java applications. See the [README](../../README.md#programmatic-api) for the full builder options table.

### Minimal example

```java
LoopyAgent agent = LoopyAgent.builder()
    .workingDirectory(Path.of("/path/to/project"))
    .build();

LoopyResult result = agent.run("add input validation to UserController");
System.out.println(result.output());
```

### Multi-step with session memory

Context is preserved across `run()` calls by default:

```java
LoopyAgent agent = LoopyAgent.builder()
    .workingDirectory(workspace)
    .maxTurns(80)
    .build();

agent.run("read the codebase and plan a refactoring of the service layer");
agent.run("now execute the plan");  // sees the previous conversation
```

Disable with `.sessionMemory(false)` for stateless batch runs.

### Custom system prompt

```java
LoopyAgent agent = LoopyAgent.builder()
    .workingDirectory(workspace)
    .systemPrompt("You are a Spring Security expert. Focus on authentication flows.")
    .model("claude-sonnet-4-6")
    .costLimit(2.0)
    .build();
```

### Custom endpoints (vLLM, LM Studio)

```java
LoopyAgent agent = LoopyAgent.builder()
    .baseUrl("http://localhost:1234/v1")
    .apiKey("lm-studio")
    .model("local-model")
    .workingDirectory(workspace)
    .build();
```
