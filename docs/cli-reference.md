# CLI Reference

## Usage

```
loopy [OPTIONS]
```

## Options

| Flag | Description | Default |
|------|-------------|---------|
| `-d, --directory <path>` | Set the agent's working directory | Current directory |
| `-m, --model <name>` | Anthropic model ID | `claude-sonnet-4-20250514` |
| `-t, --max-turns <n>` | Maximum agent loop iterations per task | `20` |
| `-p, --print <prompt>` | Run a single task and exit (print mode) | — |
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

### `/forge-agent`

Scaffolds an agent experiment project from a YAML brief.

```
/forge-agent --brief path/to/brief.yaml
/forge-agent --brief brief.yaml --output ~/projects/new-experiment
```

See [Forge Agent](forge-agent.md) for details on the brief format and customization pipeline.

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude models | Yes (except echo mode) |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Error (missing API key, agent failure, invalid arguments) |
