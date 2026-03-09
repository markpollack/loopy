# Getting Started

This guide walks you through installing Loopy, setting up your API key, and running your first agent session.

## Installation

### Option 1: Fat JAR (recommended)

```bash
curl -LO https://github.com/markpollack/loopy/releases/download/v0.2.0/loopy-0.2.0-SNAPSHOT.jar
java -jar loopy-0.2.0-SNAPSHOT.jar
```

### Option 2: Build from source

```bash
git clone https://github.com/markpollack/loopy.git
cd loopy
./mvnw package -DskipTests
java -jar target/loopy-0.2.0-SNAPSHOT.jar
```

## Prerequisites

### Java 21+

Loopy requires Java 21 or later. Check your version:

```bash
java -version
```

Install via [SDKMAN](https://sdkman.io/) (recommended):

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.9-librca
```

### API Key

Loopy supports three providers. Set the key for your preferred provider:

| Provider | Variable | Get a key |
|----------|----------|-----------|
| Anthropic (default) | `ANTHROPIC_API_KEY` | [console.anthropic.com](https://console.anthropic.com/) |
| OpenAI | `OPENAI_API_KEY` | [platform.openai.com](https://platform.openai.com/) |
| Google Gemini | `GOOGLE_API_KEY` | [aistudio.google.com](https://aistudio.google.com/) |

```bash
export ANTHROPIC_API_KEY=sk-ant-api03-...
```

Add this to your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.profile`) so it persists.

To use a non-default provider:

```bash
loopy --provider openai
loopy --provider google-genai
```

**Without an API key**, Loopy's TUI launches in echo mode — useful for testing the interface and slash commands, but the agent won't process tasks.

## Your First Session

### Interactive TUI

Launch Loopy in your project directory:

```bash
cd ~/projects/my-app
loopy
```

You'll see the Loopy logo and a text input area. Type a message and press Enter:

```
> Add a health check endpoint to this Spring Boot app
```

The agent will read your project files, plan an approach, and make changes using its tools (bash, file read/write, grep, etc.). A spinner shows while the agent is thinking.

### Single-Shot Task

For quick, non-interactive tasks:

```bash
loopy -p "create a .gitignore for a Java project"
```

The agent runs, prints its response to stdout, and exits.

### Working Directory

By default, Loopy uses the current directory. Override with `-d`:

```bash
loopy -d ~/projects/other-app
```

### Model Selection

The default model depends on the provider. Override with `-m`:

```bash
loopy -m claude-haiku-4-5-20251001
loopy --provider openai -m gpt-4o-mini
```

### CLAUDE.md Context

Loopy automatically reads `CLAUDE.md` from the working directory and includes it in the agent's system prompt. This is the same convention used by Claude Code — put project-specific instructions, architecture notes, or coding conventions in `CLAUDE.md` and the agent will follow them.

## Next Steps

- [CLI Reference](cli-reference.md) — all commands and flags
- [Configuration](configuration.md) — environment variables, custom endpoints, model selection
- [Architecture](architecture.md) — how Loopy works internally
- [Forge Agent](forge-agent.md) — scaffolding agent experiment projects
