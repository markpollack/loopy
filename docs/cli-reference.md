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

### `/forge-agent`

Scaffolds an agent experiment project from a YAML brief.

```
/forge-agent --brief path/to/brief.yaml
/forge-agent --brief brief.yaml --output ~/projects/new-experiment
```

See [Forge Agent](forge-agent.md) for details on the brief format and customization pipeline.

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
