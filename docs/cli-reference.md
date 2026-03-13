# CLI Reference

## Usage

```
loopy [OPTIONS]
```

## Options

| Flag | Description | Default |
|------|-------------|---------|
| `-d, --directory <path>` | Set the agent's working directory | Current directory |
| `-m, --model <name>` | Model ID | Per-provider (see [Configuration](configuration.md)) |
| `-t, --max-turns <n>` | Maximum agent loop iterations per task | `20` |
| `-p, --print <prompt>` | Run a single task and exit (print mode) | — |
| `--provider <name>` | AI provider: `anthropic`, `openai`, `google-genai` | `anthropic` |
| `--base-url <url>` | Custom API base URL (for vLLM, LM Studio, etc.) | — |
| `--debug` | Verbose agent activity (turns, tool calls, cost) on stderr | — |
| `--repl` | Start in REPL mode (readline loop) | — |
| `--help` | Print usage and exit | — |
| `--version` | Print version and exit | — |

Without `-p` or `--repl`, Loopy starts in **TUI mode** (default).

## Execution Modes

### TUI Mode (default)

```bash
loopy
loopy -d ~/projects/my-app
loopy -m claude-haiku-4-5-20251001 -t 30
```

Full terminal UI with:
- Chat history display
- Async agent calls with spinner animation
- Slash command support
- Enter key gated while agent is thinking (prevents overlapping requests)

### Print Mode

```bash
loopy -p "explain the main method in this project"
loopy -p "add error handling to the controller" -d ~/projects/api
```

Runs a single task, prints the agent's response to **stdout**, and exits. Status messages go to **stderr**. Returns exit code 0 on success, 1 on failure.

Useful for scripting and CI pipelines:

```bash
# Pipe output to a file
loopy -p "generate a README for this project" > README.md

# Chain with other commands
loopy -p "list all TODO comments" | grep -c "TODO"
```

### REPL Mode

```bash
loopy --repl
```

Simple readline prompt for multi-turn conversations without the full TUI. Supports the same slash commands. Type `/quit` or Ctrl+D to exit.

## Slash Commands

Available in TUI and REPL modes. Intercepted before reaching the agent — no tokens consumed.

### `/help`

Lists all available slash commands.

### `/clear`

Clears the agent's session memory. The next message starts a fresh conversation with no prior context.

### `/quit`

Exits Loopy cleanly.

### `/btw`

Ask a side question without interrupting your session context. A stateless single LLM call — the question and answer are shown in the TUI but are never added to the agent's conversation history.

```
/btw <question>
```

```
/btw what's the difference between @RestController and @Controller?
/btw how do I enable H2 console in Spring Boot?
/btw what does spring.jpa.hibernate.ddl-auto=update do?
/btw is Testcontainers supported in Spring Boot 3+?
/btw what's the default port for a Spring Boot app?
```

Useful for quick reference lookups while keeping your agent session focused on the main task.

### `/skills`

Discover, search, install, and manage domain skills. Skills teach agents domain expertise — curated knowledge packages that make agents smarter.

```
/skills                        # List installed/discovered skills
/skills list                   # Same as above
/skills search <query>         # Search curated catalog by name, tag, description, author
/skills info <name>            # Show skill details + install paths (Loopy CLI and Maven)
/skills add <name>             # Install from catalog to ~/.claude/skills/
/skills remove <name>          # Uninstall a skill
```

Skills are discovered from three sources:
- **Project**: `.claude/skills/` in the working directory
- **Global**: `~/.claude/skills/` (installed via `/skills add`)
- **Classpath**: Maven dependencies with `META-INF/skills/` (SkillsJars)

Spring AI developers can also add skills as Maven dependencies — no Loopy CLI needed:

```xml
<dependency>
  <groupId>com.skillsjars</groupId>
  <artifactId>skill-artifact-name</artifactId>
</dependency>
```

Use `/skills info <name>` to see both install paths.

### `/boot-new`

Scaffold a new Spring Boot project from a bundled template. Accepts structured flags or plain natural language.

```
/boot-new --template <name> --name <project-name> --group <group-id> [--no-llm]
/boot-new <natural language description>
```

| Template | Description |
|----------|-------------|
| `spring-boot-minimal` | Bare Spring Boot, no web, no persistence |
| `spring-boot-rest` | REST API with web, validation, MockMvc tests |
| `spring-boot-jpa` | REST + JPA with H2 for tests |
| `spring-ai-app` | Spring AI app with ChatClient wiring |

```
# Structured flags
/boot-new --template spring-boot-rest --name products-api --group com.acme
/boot-new --template spring-boot-jpa --name inventory --group com.example --no-llm
/boot-new --name my-agent --group io.myorg --template spring-ai-app
/boot-new --name widget-service --group com.corp --java-version 17 --no-llm

# Natural language — agent extracts name, group, template, Java version
/boot-new a REST API called orders-api for com.acme
/boot-new create a JPA project named catalog-service for com.corp with Java 17
/boot-new I need a minimal Spring Boot app named hello-world for io.example
/boot-new scaffold a Spring AI application for com.myorg called assistant-bot
/boot-new REST + JPA service named inventory-api, group com.warehouse, Java 21
```

Use `--no-llm` (structured path only) to skip the optional AI customization pass.

### `/boot-setup`

One-time preferences wizard for Spring Boot scaffolding. Prompts for groupId, Java version, always-add dependencies, and preferred database. Saved to `~/.config/loopy/boot/preferences.yml` and applied automatically to every subsequent `/boot-new`.

```
/boot-setup
```

`/boot-new` redirects here automatically on first use if no preferences exist yet.

### `/starters`

Discover Agent Starters and get suggestions for your project.

```
/starters                      # List available starters
/starters search <query>       # Search by name, description, or trigger
/starters info <name>          # Details + Maven coordinates
/starters suggest              # Suggest starters based on your pom.xml
```

### `/boot-add`

Bootstrap domain capabilities into an existing Spring Boot project: analyze the project structure, add the Agent Starter dependency, and optionally generate domain-specific code.

```
/boot-add <starter-name>
/boot-add <starter-name> --no-agent
/boot-add my-lib --coords com.example:my-lib:1.0.0
```

Requires `pom.xml` in the working directory. Writes `PROJECT-ANALYSIS.md` to the project root.

### `/boot-modify`

Apply structural modifications to an existing Spring Boot project using natural language.

```
/boot-modify <intent>
```

Your intent is routed to MiniAgent, which selects the right `@Tool` method based on what you asked. All POM mutations are deterministic (Maven object model — no AI-generated XML). Any natural language variation of an intent works.

**Examples:**

```
# Java version
/boot-modify set java version 21
/boot-modify upgrade to Java 21
/boot-modify use Java 17

# POM cleanup
/boot-modify clean pom
/boot-modify remove empty fields from pom.xml

# Native image
/boot-modify add native image support
/boot-modify enable GraalVM compilation
/boot-modify remove native image support

# Observability and security
/boot-modify add actuator
/boot-modify I need health check endpoints
/boot-modify add spring security
/boot-modify add authentication

# CI/CD
/boot-modify add basic CI workflow
/boot-modify add GitHub Actions for Maven
/boot-modify add multi-arch CI
/boot-modify build native for ARM64 and x86

# Dependency management
/boot-modify add dependency org.springframework.boot:spring-boot-starter-web
/boot-modify add org.mapstruct:mapstruct
/boot-modify add com.example:my-library:2.1.0
/boot-modify remove the h2 dependency
/boot-modify drop org.postgresql:postgresql

# Code style
/boot-modify add spring format enforcement
/boot-modify enforce Spring Java Format
```

See [Spring Boot Scaffolding](boot-scaffolding.md) for the full operation reference.

### `/forge-agent`

Scaffolds an agent experiment project from a YAML brief.

```
/forge-agent --brief path/to/brief.yaml
/forge-agent --brief brief.yaml --output ~/projects/new-experiment
```

See [Forge Agent](forge-agent.md) for details on the brief format and customization pipeline.

### `/session`

Save, list, and restore conversation sessions. Sessions are stored as JSON files in `~/.config/loopy/sessions/`. Auto-saved on `/quit`.

```
/session save [name]    # Save current session (auto-named if omitted)
/session list           # List saved sessions with timestamps and sizes
/session load <id>      # Restore a previous session (partial ID prefix accepted)
```

```
/session save
/session save my-refactor
/session list
/session load 20260313
```

## Multi-Provider Examples

```bash
# Anthropic (default)
loopy -p "explain this codebase"

# OpenAI
loopy --provider openai -p "add input validation"

# Google Gemini
loopy --provider google-genai -p "write unit tests"

# Custom endpoint (vLLM, LM Studio)
loopy --provider openai --base-url http://localhost:1234/v1 -p "hello"

# Verbose user-facing debug (turns, tool calls, cost on stderr)
loopy --debug --repl

# Developer file logging (Spring AI internals, token math)
LOOPY_DEBUG_LOG=1 loopy --repl
LOOPY_DEBUG_LOG=/tmp/loopy.log loopy -p "hello"
```

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `ANTHROPIC_API_KEY` | Anthropic API key | Yes for `--provider anthropic` (default) |
| `OPENAI_API_KEY` | OpenAI API key | Yes for `--provider openai` |
| `GOOGLE_API_KEY` | Google AI API key | Yes for `--provider google-genai` |
| `LOOPY_DEBUG_LOG` | Developer file logging path (set to `1` for default `~/.local/state/loopy/logs/loopy-debug.log`) | No |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Error (missing API key, agent failure, invalid arguments) |
