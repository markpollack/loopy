# Subagents

Instead of one generalist agent doing everything, delegate to specialized agents. Subagents keep context windows focused — preventing the clutter that degrades performance on complex, multi-step tasks.

Loopy's subagent support is built on [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) `TaskTool`, a portable Spring AI implementation inspired by Claude Code's subagents. It enables hierarchical agent architectures where specialized subagents handle focused tasks in dedicated context windows, returning only essential results to the parent.

## How It Works

The main agent (Loopy's MiniAgent) has access to the `Task` tool. When given a complex task, it can delegate subtasks to specialized subagents. Each subagent:

- Runs in its own **isolated context window** — no conversation history pollution
- Has a **custom system prompt** tailored to its domain
- Has **configurable tool access** — only the tools it needs
- Can use a **different model** — route cheap tasks to Haiku, complex analysis to Opus

Results flow back to the main agent as a summary; intermediate steps stay in the subagent's context.

```
User → Main Agent (Loopy MiniAgent)
                 │
         ┌───────┼───────┐
         ▼       ▼       ▼
     Explore   Plan   General-Purpose
     (read-   (arch)  (full access)
      only)
         │       │       │
         └───────┴───────┘
                 │
         Main Agent synthesizes
                 │
               User
```

## Built-in Subagents

Four subagents are available by default — no configuration required:

| Subagent | Purpose | Tools |
|----------|---------|-------|
| `Explore` | Fast, read-only codebase exploration — find files, search code, analyze contents | Read, Grep, Glob, ListDirectory |
| `General-Purpose` | Multi-step research and execution with full read/write access | All tools |
| `Plan` | Software architect for designing implementation strategies and identifying trade-offs | Read-only + search |
| `Bash` | Command execution specialist for git operations, builds, and terminal tasks | Bash only |

The main agent automatically selects the appropriate subagent based on the task — no explicit routing instructions needed.

## Custom Subagents

Place Markdown files in `.claude/agents/` in your working directory:

```
your-project/
└── .claude/
    └── agents/
        ├── code-reviewer.md
        └── test-runner.md
```

### Subagent File Format

```markdown
---
name: code-reviewer
description: Expert code reviewer. Use proactively after writing code.
tools: Read, Grep, Glob
disallowedTools: Edit, Write
model: sonnet
---

You are a senior code reviewer with expertise in software quality.

**When Invoked:**
1. Run `git diff` to see recent changes
2. Focus analysis on modified files
3. Check surrounding code context

**Review Checklist:**
- Code clarity and readability
- Proper naming conventions
- Error handling
- Security vulnerabilities

**Output:** Clear, actionable feedback with file references.
```

### Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier (lowercase with hyphens) |
| `description` | Yes | When to use this subagent — the main agent reads this to decide when to delegate |
| `tools` | No | Allowed tool names (inherits all if omitted) |
| `disallowedTools` | No | Tools to explicitly deny |
| `model` | No | Model preference: `haiku`, `sonnet`, `opus` |

> **Note:** Subagents cannot spawn their own subagents. Do not include `Task` in a subagent's tools list.

## Multi-Model Routing

Route subagents to different models based on task complexity. The `model` field in the subagent definition maps to a named chat client:

| Model alias | Typical use |
|-------------|-------------|
| `haiku` | Fast exploration, simple lookups |
| `sonnet` | Default — general coding tasks |
| `opus` | Complex analysis, architecture decisions |

The main agent's model is used by default if no `model` is specified in the subagent definition.

## Example: Code Review Flow

```
User: "Review my changes and run the tests"

Main agent:
  → Task(Explore, "Find all modified files since last commit")
  → Task(code-reviewer, "Review these changes: [file list]")
  → Task(Bash, "Run mvn test and return the summary")
  → Synthesize results and report
```

Each subtask runs in isolation. The main agent never sees intermediate bash output from the test run — only the final summary returned by the Bash subagent.

## Background Execution

Long-running subagents can execute asynchronously. The main agent launches a background task and continues working, polling for results when needed. Use the `TaskOutput` tool to retrieve results from background tasks.

## Trying It Out

The easiest way to verify subagent support is REPL mode — responses print inline so you can see delegation happening in real time.

```bash
java -jar target/loopy-0.2.0-SNAPSHOT.jar --repl
```

### Verify available tools and subagents

```
> what tools do you have? list them including any subagents
```

Expected output includes `Task` and `TaskOutput` in the tools list, plus a subagents section listing the four built-in agents (`general-purpose`, `Explore`, `Plan`, `Bash`) and any custom agents from `.claude/agents/` with valid frontmatter.

### Delegate to a subagent

```
> use the Explore subagent to count the number of Java classes in the agent package
```

The main agent invokes `Task(Explore, ...)`. The Explore subagent runs in an isolated context, scans the files, and returns a count. You will see output like:

```
I'll use the Explore subagent to count the Java classes in the agent package.
[delegates to Explore]
The Explore subagent found N Java classes in the agent/ package: [list of class names]
```

### Background task with TaskOutput

```
> run a background task using the bash subagent to list files in the current directory,
  then use TaskOutput to retrieve the result
```

The main agent launches the Bash subagent asynchronously, receives a task ID, then calls `TaskOutput` with that ID to retrieve the result once it completes.

### Custom subagents

Any Markdown file in `.claude/agents/` (project) with valid YAML frontmatter (`name:` and `description:` fields) is automatically loaded. Files without those frontmatter fields are skipped — this is intentional, as Claude Code creates agent files in a different format.

Example custom agent (`.claude/agents/code-reviewer.md`):

```markdown
---
name: code-reviewer
description: Reviews code for quality and correctness
tools: Read, Grep, Glob
---

You are a senior code reviewer. When invoked, analyze the specified files
for quality issues, naming conventions, and potential bugs.
```

After placing this file, restart Loopy and ask the agent to list its subagents — `code-reviewer` will appear alongside the four built-ins.

## Disabling Subagents

To disable the `Task` tool entirely (e.g., for strict single-agent mode):

```bash
# Programmatic API
MiniAgent agent = MiniAgent.builder()
    .disabledTools(Set.of("Task"))
    ...
    .build();
```

The `disabledTools` builder method accepts any tool name from the [Agent Tools](../README.md#agent-tools) table.
