# Configuration

Loopy is currently configured via CLI flags and environment variables. A configuration file is planned for a future release.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ANTHROPIC_API_KEY` | API key for Anthropic Claude models | *required* |

Set your API key:

```bash
export ANTHROPIC_API_KEY=sk-ant-api03-...
```

## Model Selection

The default model is `claude-sonnet-4-20250514`. Override per-session:

```bash
# CLI flag
loopy -m claude-haiku-4-5-20251001

# For faster, cheaper responses
loopy -m claude-haiku-4-5-20251001

# For maximum capability
loopy -m claude-opus-4-6
```

Available models are determined by your Anthropic API plan. See [Anthropic's model documentation](https://docs.anthropic.com/en/docs/about-claude/models) for the full list.

## Agent Limits

### Max Turns

Each message you send triggers an agent loop that runs until the task is complete or the turn limit is reached:

```bash
loopy -t 30  # Allow up to 30 tool-calling iterations
```

Default is 20 turns in CLI mode, 80 in the programmatic API.

### Cost Limit

The programmatic API enforces a cost limit (default $5.00 per `LoopyAgent` instance). The agent stops when estimated cost exceeds the limit. This is not yet exposed as a CLI flag.

### Command Timeout

Individual tool calls (bash commands, file operations) time out after 120 seconds. This is configurable via the programmatic API.

## Custom API Endpoints

The programmatic API supports Anthropic-compatible endpoints for local models or alternative providers:

```java
LoopyAgent agent = LoopyAgent.builder()
    .baseUrl("http://localhost:1234/v1")
    .apiKey("lm-studio")  // Some local servers accept any string
    .model("local-model-name")
    .workingDirectory(workspace)
    .build();
```

HTTP/1.1 fallback is applied automatically when `baseUrl` is set, since local servers (vLLM, LM Studio) typically don't support HTTP/2.

**Note:** Custom endpoints are only available via the programmatic API. CLI support for `--base-url` is planned.

## CLAUDE.md Auto-Injection

The embedded agent (via [Claude Agent SDK for Java](https://github.com/anthropics/claude-agent-sdk-java)) automatically reads `CLAUDE.md` from the working directory and appends it to the system prompt. Place a `CLAUDE.md` file in your project root to give the agent project-specific context. Example:

```markdown
# Project Instructions

This is a Spring Boot 3.x application using Java 21.

## Conventions
- Use constructor injection, not field injection
- All REST endpoints return ResponseEntity
- Tests use @WebMvcTest for controller tests

## Architecture
- `controller/` — REST endpoints
- `service/` — Business logic
- `repository/` — Data access (Spring Data JPA)
```

## Context Compaction

For long sessions, Loopy automatically summarizes older messages when estimated token usage exceeds 50% of the model's context limit. Summarization uses Claude Haiku for cost efficiency. This prevents context window overflow while preserving important context.

Compaction settings are configurable via the programmatic API:

```java
LoopyAgent agent = LoopyAgent.builder()
    .compactionEnabled(true)        // default: true
    .compactionThreshold(0.5)       // trigger at 50% of limit
    .contextLimit(200_000)          // model's context window
    // ...
    .build();
```
