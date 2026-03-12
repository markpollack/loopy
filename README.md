# Loopy

A loop-driven interactive coding agent CLI for Java developers. Built on [Spring AI](https://docs.spring.io/spring-ai/reference/) with an embedded agent loop, modern terminal UI, and domain skills.

Loopy is the entry point for **knowledge-directed execution** — the idea that curated domain knowledge + structured execution contribute more to agent quality than model choice alone.

## Highlights

- **Spring Boot scaffolding** — create, analyze, extend, and modify Spring Boot projects with `/boot-new`, `/boot-add`, `/starters`, and `/boot-modify`. No calls to `start.spring.io` — all knowledge is bundled.
- **Skills** — domain knowledge that makes agents smarter. Discover, install, and create skills from a curated catalog of 23+ skills. Skills follow the [agentskills.io](https://agentskills.io) spec and work in any agentic CLI
- **Multi-provider** — Anthropic (default), OpenAI, Google Gemini. Switch with `--provider`
- **Three modes** — interactive TUI, single-shot print (`-p`), REPL (`--repl`)
- **Cost visibility** — per-turn token usage and estimated cost
- **Context compaction** — automatic summarization for long sessions
- **CLAUDE.md auto-injection** — project-specific instructions, same convention as Claude Code
- **Forge agent** — scaffold agent experiment projects from YAML briefs

## Documentation

- [Getting Started](docs/getting-started.md) — installation, API key setup, first session
- [CLI Reference](docs/cli-reference.md) — all commands, flags, and modes
- [Spring Boot Scaffolding](docs/boot-scaffolding.md) — `/boot-new`, `/starters`, `/boot-add`, `/boot-modify`
- [Subagents](docs/subagents.md) — built-in subagents, custom subagents, multi-model routing
- [Configuration](docs/configuration.md) — environment variables, model selection, custom endpoints
- [Architecture](docs/architecture.md) — four-layer design, data flow, quality guardrails
- [Forge Agent](docs/forge-agent.md) — scaffolding experiment projects with `/forge-agent`

## Prerequisites

- **Java 21+** — check with `java -version`. Install via [SDKMAN](https://sdkman.io/): `sdk install java 21.0.9-librca`
- **API key** for at least one provider:

| Provider | Environment Variable | Get a key |
|----------|---------------------|-----------|
| Anthropic (default) | `ANTHROPIC_API_KEY` | [console.anthropic.com](https://console.anthropic.com/) |
| OpenAI | `OPENAI_API_KEY` | [platform.openai.com](https://platform.openai.com/) |
| Google Gemini | `GOOGLE_API_KEY` | [aistudio.google.com](https://aistudio.google.com/) |

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Add the export to your shell profile (`~/.bashrc`, `~/.zshrc`) to persist across sessions.

## Quick Start

### Download fat JAR (easiest)

```bash
curl -LO https://github.com/markpollack/loopy/releases/download/v0.2.0/loopy-0.2.0-SNAPSHOT.jar
java -jar loopy-0.2.0-SNAPSHOT.jar
```

### Build from source

```bash
git clone https://github.com/markpollack/loopy.git
cd loopy
./mvnw package
java -jar target/loopy-0.2.0-SNAPSHOT.jar
```

## Execution Modes

Loopy supports three modes for different workflows:

### Interactive TUI (default)

Full terminal UI with chat history, async agent calls, and spinner animation:

```bash
loopy
```

Type messages to chat with the agent. Use slash commands (see below) for built-in operations. The TUI uses an Elm Architecture via [tui4j](https://github.com/williamcallahan/tui4j).

### Print Mode (single-shot)

Run a single task and exit — useful for scripting and CI:

```bash
loopy -p "refactor this class to use the builder pattern"
```

Output goes to stdout, status to stderr. Exit code 0 on success, 1 on failure.

### REPL Mode (readline)

Simple line-by-line prompt for testing and quick tasks:

```bash
loopy --repl
```

## CLI Options

| Flag | Description | Default |
|------|-------------|---------|
| `-d, --directory <path>` | Working directory | Current directory |
| `-m, --model <name>` | Model to use | Per-provider (see [Configuration](docs/configuration.md)) |
| `-t, --max-turns <n>` | Maximum agent loop iterations | `20` |
| `-p, --print <prompt>` | Single-shot print mode | — |
| `--provider <name>` | AI provider: `anthropic`, `openai`, `google-genai` | `anthropic` |
| `--base-url <url>` | Custom API base URL (vLLM, LM Studio) | — |
| `--debug` | Verbose agent activity (turns, tool calls, cost) on stderr | — |
| `--repl` | REPL mode (readline loop) | — |
| `--help` | Print usage information | — |
| `--version` | Print version | — |

## Slash Commands

In TUI and REPL modes, lines starting with `/` are intercepted before reaching the agent — no LLM tokens wasted.

| Command | Description |
|---------|-------------|
| `/help` | List available commands |
| `/clear` | Clear session memory (start fresh conversation) |
| `/quit` | Exit Loopy |
| `/skills` | Discover, search, install, and manage domain skills |
| `/boot-new` | Scaffold a new Spring Boot project from a bundled template |
| `/starters` | Discover Agent Starters; suggest by pom.xml triggers |
| `/boot-add` | Bootstrap domain capabilities into an existing Spring Boot project |
| `/boot-modify` | Apply structural modifications — set Java version, add native support, add CI, clean pom, and more |
| `/forge-agent --brief <path>` | Bootstrap an agent experiment project from a YAML brief |

## Features

### Skills (Domain Knowledge)

Skills are curated knowledge packages that make agents smarter. They follow the [agentskills.io](https://agentskills.io) specification and work in 40+ agentic CLIs — not just Loopy.

```bash
loopy --repl
> /skills                          # List installed and discovered skills
> /skills search testing           # Search the curated catalog
> /skills info systematic-debugging  # Skill details + install instructions
> /skills add systematic-debugging   # Install to ~/.claude/skills/
> /skills remove systematic-debugging  # Uninstall
```

Loopy discovers skills from three sources:

| Source | Path | How it gets there |
|--------|------|-------------------|
| Project | `.claude/skills/*/SKILL.md` | Team-shared skills checked into the repo |
| Global | `~/.claude/skills/*/SKILL.md` | `/skills add` from the curated catalog |
| Classpath | `META-INF/skills/SKILL.md` in JARs | Maven dependency |

**Progressive disclosure** — the agent sees skill names and descriptions, and loads full content only when relevant to the task. No tokens wasted on unused skills.

**Curated catalog** — 23 skills from 8 publishers covering testing, debugging, security, code review, architecture, and more. Run `/skills search` to browse.

#### Creating Custom Skills

Place a `SKILL.md` file in `.claude/skills/<name>/` (project) or `~/.claude/skills/<name>/` (global):

```markdown
---
name: my-team-conventions
description: Coding conventions for my team's Spring Boot codebase
---

# Instructions

When working on this codebase:
- Use constructor injection, never field injection
- All REST endpoints return ProblemDetail for errors
- Tests use @WebMvcTest with MockMvc
```

### Spring Boot Scaffolding

Loopy includes a full Spring Boot project lifecycle — create, discover capabilities, extend, and modify — with no dependency on `start.spring.io` or the Spring Initializr library. Knowledge is bundled; execution is deterministic.

```bash
# Create a new project
/boot-new --template spring-boot-rest --name products-api --group com.acme

# Discover what capabilities are available for your project
/starters suggest

# Bootstrap domain capabilities into an existing project
/boot-add spring-ai-starter-data-jpa

# Modify project structure
/boot-modify set java version 21
/boot-modify add native image support
/boot-modify add multi-arch CI
/boot-modify I need health check endpoints
```

**`/boot-modify`** understands natural language. For common operations it uses no AI at all (keyword shortcuts). For natural-language variations it uses a single lightweight classification call, then executes deterministically. The AI never writes POM XML — all POM changes go through Maven's own object model.

See [Spring Boot Scaffolding](docs/boot-scaffolding.md) for the full reference.

### CLAUDE.md Auto-Injection

Loopy automatically reads `CLAUDE.md` from the working directory and appends it to the agent's system prompt. This gives the agent project-specific context without any CLI flags — the same convention used by Claude Code.

### Cost Visibility

Every response shows token usage and estimated cost:

```
tokens: 1234/567 | cost: $0.0089
```

### Context Compaction

Long sessions can exceed the model's context window. Loopy automatically summarizes older messages when estimated tokens exceed 50% of the model limit, using a cheap model from the same provider (e.g., Haiku for Anthropic, gpt-4o-mini for OpenAI, gemini-2.5-flash-lite for Gemini). This reuses the same API credentials via a per-request model override.

### Agent Tools

The embedded agent has access to:

| Tool | Description |
|------|-------------|
| `bash` | Execute shell commands |
| `Read` | Read file contents |
| `Write` | Create or overwrite files |
| `Edit` | Make targeted edits to existing files |
| `Glob` | Find files by pattern |
| `Grep` | Search file contents |
| `Skill` | Load domain skills for task-relevant expertise |
| `Submit` | Submit final answer (ends the agent loop) |
| `TodoWrite` | Track work items |
| `Task` | Delegate to specialized subagents — each runs in an isolated context window with its own system prompt, tools, and optional model. Four built-in subagents: `Explore`, `General-Purpose`, `Plan`, `Bash`. Custom subagents defined as Markdown files in `.claude/agents/`. See [Subagents](docs/subagents.md). |
| `TaskOutput` | Retrieve results from background subagents launched with `run_in_background: true`. Supports blocking (`block: true`, default) and non-blocking poll modes. |
| `AskUserQuestionTool` | Ask the user for clarification (TUI only) |
| `WebSearch` | Web search (requires `BRAVE_API_KEY`) |
| `WebFetch` | Fetch and summarize web pages (requires `BRAVE_API_KEY`) |

### Echo Mode (no API key)

If no API key is set for the selected provider, the TUI launches in echo mode — useful for testing the UI and slash commands without consuming API tokens.

## Programmatic API

Embed Loopy's agent in other Java projects via `LoopyAgent`:

```java
LoopyAgent agent = LoopyAgent.builder()
    .model("claude-haiku-4-5-20251001")
    .workingDirectory(workspace)
    .systemPrompt("You are a Spring Boot expert.")
    .maxTurns(80)
    .build();

// Single task
LoopyResult result = agent.run("add input validation to UserController");

// Multi-step with session memory (context preserved across calls)
LoopyResult plan = agent.run("plan the refactoring of the service layer");
LoopyResult act = agent.run("now execute the plan");
```

### Builder Options

| Method | Description | Default |
|--------|-------------|---------|
| `.model(String)` | Anthropic model ID | `claude-sonnet-4-6` |
| `.workingDirectory(Path)` | Agent's working directory | *required* |
| `.systemPrompt(String)` | Custom system prompt | Built-in coding prompt |
| `.maxTurns(int)` | Max agent loop iterations | `80` |
| `.apiKey(String)` | API key override | `ANTHROPIC_API_KEY` env var |
| `.baseUrl(String)` | Custom API endpoint (vLLM, LM Studio) | Anthropic default |
| `.sessionMemory(boolean)` | Preserve context across `run()` calls | `true` |
| `.compactionModelName(String)` | Model for compaction (null to disable) | `claude-haiku-4-5-20251001` |
| `.compactionThreshold(double)` | Fraction of context limit triggering compaction | `0.5` |
| `.contextLimit(int)` | Token limit for compaction calculation | `200,000` |
| `.costLimit(double)` | Max cost in dollars before stopping | `$5.00` |
| `.commandTimeout(Duration)` | Timeout for individual tool calls (bash, file ops) | `120s` |
| `.timeout(Duration)` | Overall agent loop timeout | `10 min` |
| `.disabledTools(Set<String>)` | Tools to exclude by name | none |

> **Note:** The programmatic API defaults to `claude-sonnet-4-6` via Anthropic only. CLI mode defaults are set per-provider in `application.yml` (Anthropic: `claude-sonnet-4-20250514`, OpenAI: `gpt-4o`, Gemini: `gemini-2.5-flash`).

### Custom Endpoints

Loopy supports Anthropic-compatible API endpoints (vLLM, LM Studio, etc.) via the `.baseUrl()` builder method. HTTP/1.1 fallback is applied automatically for custom endpoints:

```java
LoopyAgent agent = LoopyAgent.builder()
    .baseUrl("http://localhost:1234/v1")
    .apiKey("lm-studio")
    .model("local-model")
    .workingDirectory(workspace)
    .build();
```

## Architecture

Single Maven module with five layers:

```
io.github.markpollack.loopy
├── agent/    MiniAgent loop, AgentLoopAdvisor, BashTool, SkillsTool, observability
├── tui/      ChatScreen (Elm Architecture via tui4j), ChatEntry
├── command/  SlashCommand interface, registry, /help, /clear, /quit, /skills
├── boot/     /boot-new, /starters, /boot-add, /boot-modify, PomMutator, SAE analyzer
└── forge/    ExperimentBrief, TemplateCloner, TemplateCustomizer, /forge-agent
```

- **Agent layer** — MiniAgent is an embedded agent loop (copied from [agent-harness](https://github.com/markpollack/agent-harness), evolving independently). It runs a think-act-observe loop with tool calling. SkillsTool provides progressive skill discovery.
- **TUI layer** — Elm Architecture UI via tui4j. Agent calls run on a background thread; a spinner animates while waiting; Enter is gated to prevent overlapping calls.
- **Command layer** — Slash commands are intercepted in `ChatScreen.submitInput()` before reaching the agent. New commands implement the `SlashCommand` interface and register in `SlashCommandRegistry`.
- **Boot layer** — Spring Boot scaffolding: four commands, bundled templates, `BootProjectAnalyzer` (SAE), `PomMutator` (Maven object model), `RecipeCatalog` (deterministic operations), `RecipeClassifier` (lightweight AI classification).
- **Forge layer** — `/forge-agent` scaffolds agent experiment projects from YAML briefs: clone template, rename packages, update POM, generate config and README.

## License

[Business Source License 1.1](LICENSE) — free for non-production and limited production use. Converts to Apache 2.0 on the Change Date.
