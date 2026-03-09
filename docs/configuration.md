# Configuration

Loopy is configured via `application.yml`, CLI flags, and environment variables. Spring Boot auto-configuration handles provider wiring.

## Environment Variables

| Variable | Description |
|----------|-------------|
| `ANTHROPIC_API_KEY` | Anthropic API key (for `--provider anthropic`, the default) |
| `OPENAI_API_KEY` | OpenAI API key (for `--provider openai`) |
| `GOOGLE_API_KEY` | Google AI API key (for `--provider google-genai`) |
| `LOOPY_DEBUG_LOG` | Developer file logging. Set to `1` for default path (`~/.local/state/loopy/logs/loopy-debug.log`) or a custom file path. Captures Spring AI internals, token math, advisor chain details. |

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

## Debugging

Loopy separates user-facing verbosity from developer file logging:

### `--debug` (user-facing)

Shows agent activity on stderr — turn numbers, tool call names with args, duration, and cost per turn:

```bash
loopy --debug -p "create a hello world app"
```

### `LOOPY_DEBUG_LOG` (developer)

Full logback DEBUG output to a file — Spring AI advisor chain, token math, request/response details:

```bash
# Default path
LOOPY_DEBUG_LOG=1 loopy --repl

# Custom path
LOOPY_DEBUG_LOG=/tmp/loopy.log loopy -p "hello"
```

## Provider Selection

Loopy supports three providers. The `--provider` flag selects which one is active:

```bash
# Anthropic (default)
loopy

# OpenAI
loopy --provider openai

# Google Gemini
loopy --provider google-genai
```

Only one provider is active per session. The flag sets `spring.ai.model.chat` before the Spring context boots, ensuring exactly one `ChatModel` bean is created.

## application.yml

Default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-sonnet-4-20250514
          max-tokens: 4096
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-4o
    google:
      genai:
        api-key: ${GOOGLE_API_KEY:}
        chat:
          options:
            model: gemini-2.5-flash
```

Override any property via environment variables or CLI flags.

## Model Selection

### Model Tiers by Provider

Each provider has a "sonnet-class" (default, strong reasoning) and "haiku-class" (cheap, used for compaction) model:

| Provider | Sonnet-class (default) | Haiku-class (compaction) |
|----------|----------------------|--------------------------|
| Anthropic | `claude-sonnet-4-20250514` | `claude-haiku-4-5-20251001` |
| OpenAI | `gpt-4o` | `gpt-4o-mini` |
| Google Gemini | `gemini-2.5-flash` | `gemini-2.5-flash-lite` |

Override the default model per-session with `-m`:

```bash
loopy -m claude-haiku-4-5-20251001
loopy --provider openai -m gpt-4o-mini
loopy --provider google-genai -m gemini-2.5-flash
```

The compaction model is selected automatically based on the provider — no configuration needed.

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

Use `--base-url` with `--provider openai` for local models (vLLM, LM Studio, Ollama):

```bash
loopy --provider openai --base-url http://localhost:1234/v1 -p "hello"
```

The programmatic API also supports custom endpoints:

```java
LoopyAgent agent = LoopyAgent.builder()
    .baseUrl("http://localhost:1234/v1")
    .apiKey("lm-studio")
    .model("local-model-name")
    .workingDirectory(workspace)
    .build();
```

HTTP/1.1 fallback is applied automatically when `baseUrl` is set.

## Agent Skills

Skills are domain knowledge packages that make agents smarter. Loopy discovers skills from three locations:

| Source | Path | How it gets there |
|--------|------|-------------------|
| Project | `.claude/skills/*/SKILL.md` | Manual — team-shared skills checked into the repo |
| Global | `~/.claude/skills/*/SKILL.md` | `/skills add <name>` from the curated catalog |
| Classpath | `META-INF/skills/SKILL.md` in JARs | Maven dependency (SkillsJars) |

Skills use progressive disclosure — the agent sees skill names and descriptions, and loads full content only when relevant to the task. No tokens wasted on unused skills.

### Installing Skills

**Via Loopy CLI** (downloads SKILL.md to `~/.claude/skills/`):
```bash
loopy --repl
> /skills search testing
> /skills add systematic-debugging
```

**Via Maven dependency** (for Spring AI applications):
```xml
<dependency>
  <groupId>com.skillsjars</groupId>
  <artifactId>obra__superpowers__systematic-debugging</artifactId>
</dependency>
```

Both paths result in the same outcome: the agent gains domain expertise.

### Creating Custom Skills

Place a `SKILL.md` file in `.claude/skills/<name>/` (project) or `~/.claude/skills/<name>/` (global):

```markdown
---
name: my-custom-skill
description: Custom conventions for my team's codebase
---

# Instructions

When working on this codebase:
- Use constructor injection, never field injection
- All REST endpoints return ProblemDetail for errors
- Tests use @WebMvcTest with MockMvc
```

See `/skills info skill-creator` for the full authoring guide.

## CLAUDE.md Auto-Injection

Loopy automatically reads `CLAUDE.md` from the working directory and appends it to the agent's system prompt. Place a `CLAUDE.md` file in your project root to give the agent project-specific context. Example:

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

For long sessions, Loopy automatically summarizes older messages when estimated token usage exceeds 50% of the model's context limit. Compaction uses a cheap model from the same provider (same API credentials, per-request model override):

| Provider | Compaction model |
|----------|-----------------|
| Anthropic | `claude-haiku-4-5-20251001` |
| OpenAI | `gpt-4o-mini` |
| Google Gemini | `gemini-2.5-flash-lite` |

Compaction settings are configurable via the programmatic API:

```java
LoopyAgent agent = LoopyAgent.builder()
    .compactionModelName("claude-haiku-4-5-20251001")  // null to disable
    .compactionThreshold(0.5)       // trigger at 50% of limit
    .contextLimit(200_000)          // model's context window
    // ...
    .build();
```
