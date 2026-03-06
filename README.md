# Loopy

A loop-driven interactive coding agent CLI. Built on Spring AI with an embedded agent loop, terminal UI, and project scaffolding.

## Quick Start

Requires Java 21+ and an `ANTHROPIC_API_KEY` environment variable.

### JBang (easiest)

```bash
jbang loopy@markpollack/loopy -p "create a hello world app"
```

### Download fat JAR

Download from the [releases page](https://github.com/markpollack/loopy/releases), then:

```bash
# Single-shot print mode
java -jar loopy-0.1.0-SNAPSHOT.jar -p "create a hello world app"

# Interactive REPL
java -jar loopy-0.1.0-SNAPSHOT.jar --repl

# Interactive TUI
java -jar loopy-0.1.0-SNAPSHOT.jar

# With options
java -jar loopy-0.1.0-SNAPSHOT.jar -d ~/projects/myapp -m claude-sonnet-4-20250514 -t 20
```

### Build from source

```bash
./mvnw package
java -jar target/loopy-0.1.0-SNAPSHOT.jar --help
```

## Architecture

Single-module CLI with four layers:

- **Agent** (`agent/`) — MiniAgent loop, AgentLoopAdvisor, BashTool, observability
- **TUI** (`tui/`) — ChatScreen (Elm Architecture via tui4j), ChatEntry
- **Command** (`command/`) — SlashCommand interface, registry, `/help`, `/clear`, `/quit`
- **Forge** (`forge/`) — ExperimentBrief, TemplateCloner, TemplateCustomizer, `/forge-agent`

## Programmatic API

Use `LoopyAgent` to embed the agent in other Java projects:

```java
LoopyAgent agent = LoopyAgent.builder()
    .model("claude-haiku-4-5-20251001")
    .workingDirectory(workspace)
    .systemPrompt(prompt)
    .maxTurns(80)
    .build();

LoopyResult result = agent.run("implement the feature");
```

Context compaction is enabled by default — old messages are summarized via Haiku when context exceeds 50% of the model limit, keeping long sessions within budget.

## Tools

The agent has access to: `bash`, `glob`, `grep`, `read_file`, `write_file`, `edit_file`, `list_files`, `Submit`, `TodoWrite`, `Task` (sub-agent delegation), and optionally `AskUserQuestion`, `BraveWebSearch`, `SmartWebFetch`.

## License

[Business Source License 1.1](LICENSE) — free for non-production and limited production use. Converts to Apache 2.0 on the Change Date.
