# Loopy

A loop-driven interactive coding agent CLI for Java developers. Built on [Spring AI](https://docs.spring.io/spring-ai/reference/) with an embedded agent loop, modern terminal UI, and project scaffolding.

Loopy is the entry point for **knowledge-directed execution** — the idea that curated domain knowledge + structured execution process contribute more to agent quality than model choice alone.

## Documentation

- [Getting Started](docs/getting-started.md) — installation, API key setup, first session
- [CLI Reference](docs/cli-reference.md) — all commands, flags, and modes
- [Configuration](docs/configuration.md) — environment variables, model selection, custom endpoints
- [Architecture](docs/architecture.md) — four-layer design, data flow, quality guardrails
- [Forge Agent](docs/forge-agent.md) — scaffolding experiment projects with `/forge-agent`

## Prerequisites

- **Java 21+** — check with `java -version`. Install via [SDKMAN](https://sdkman.io/): `sdk install java 21.0.9-librca`
- **Anthropic API key** — get one at [console.anthropic.com](https://console.anthropic.com/)

Set your API key:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Add the export to your shell profile (`~/.bashrc`, `~/.zshrc`) to persist across sessions.

## Quick Start

### JBang (easiest, no build required)

```bash
# Single-shot task
jbang loopy@markpollack/loopy -p "create a hello world Spring Boot app"

# Interactive TUI
jbang loopy@markpollack/loopy
```

### Download fat JAR

Download from the [releases page](https://github.com/markpollack/loopy/releases):

```bash
java -jar loopy-0.1.0-SNAPSHOT.jar
```

### Build from source

```bash
git clone https://github.com/markpollack/loopy.git
cd loopy
./mvnw package
java -jar target/loopy-0.1.0-SNAPSHOT.jar
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
| `-m, --model <name>` | Anthropic model to use | `claude-sonnet-4-20250514` |
| `-t, --max-turns <n>` | Maximum agent loop iterations | `20` |
| `-p, --print <prompt>` | Single-shot print mode | — |
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
| `/forge-agent --brief <path>` | Bootstrap an agent experiment project from a YAML brief |

## Features

### CLAUDE.md Auto-Injection

Loopy's embedded agent (via [Claude Agent SDK for Java](https://github.com/anthropics/claude-agent-sdk-java)) automatically reads `CLAUDE.md` from the working directory and appends it to the system prompt. This gives the agent project-specific context without any CLI flags — the same convention used by Claude Code.

### Context Compaction

Long sessions can exceed the model's context window. Loopy automatically summarizes older messages via Haiku when estimated tokens exceed 50% of the model limit, keeping the agent effective throughout extended sessions. This is enabled by default and configurable via the programmatic API.

### Agent Tools

The embedded agent has access to:

| Tool | Description |
|------|-------------|
| `bash` | Execute shell commands |
| `read_file` | Read file contents |
| `write_file` | Write files |
| `edit_file` | Edit files with diffs |
| `glob` | Find files by pattern |
| `grep` | Search file contents |
| `list_files` | List directory contents |
| `Submit` | Submit final answer |
| `TodoWrite` | Track work items |
| `Task` | Delegate to sub-agents |
| `AskUserQuestion` | Ask the user for clarification (optional) |
| `BraveWebSearch` | Web search (optional, requires API key) |
| `SmartWebFetch` | Fetch web pages (optional) |

### Echo Mode (no API key)

If `ANTHROPIC_API_KEY` is not set, the TUI launches in echo mode — useful for testing the UI and slash commands without consuming API tokens.

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
| `.compactionEnabled(boolean)` | Auto-summarize old messages | `true` |
| `.compactionThreshold(double)` | Fraction of context limit triggering compaction | `0.5` |
| `.contextLimit(int)` | Token limit for compaction calculation | `200,000` |
| `.costLimit(double)` | Max cost in dollars before stopping | `$5.00` |
| `.commandTimeout(Duration)` | Timeout for individual tool calls | `120s` |
| `.timeout(Duration)` | Overall agent loop timeout | `10 min` |
| `.disabledTools(Set<String>)` | Tools to exclude | none |

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

Single Maven module with four layers:

```
io.github.markpollack.loopy
├── agent/    MiniAgent loop, AgentLoopAdvisor, BashTool, observability
├── tui/      ChatScreen (Elm Architecture via tui4j), ChatEntry
├── command/  SlashCommand interface, registry, /help, /clear, /quit
└── forge/    ExperimentBrief, TemplateCloner, TemplateCustomizer, /forge-agent
```

- **Agent layer** — MiniAgent is an embedded agent loop (copied from [agent-harness](https://github.com/markpollack/agent-harness), evolving independently). It runs a think-act-observe loop with tool calling.
- **TUI layer** — Elm Architecture UI via tui4j. Agent calls run on a background thread; a spinner animates while waiting; Enter is gated to prevent overlapping calls.
- **Command layer** — Slash commands are intercepted in `ChatScreen.submitInput()` before reaching the agent. New commands implement the `SlashCommand` interface and register in `SlashCommandRegistry`.
- **Forge layer** — `/forge-agent` scaffolds agent experiment projects from YAML briefs: clone template, rename packages, update POM, generate config and README.

## License

[Business Source License 1.1](LICENSE) — free for non-production and limited production use. Converts to Apache 2.0 on the Change Date.
