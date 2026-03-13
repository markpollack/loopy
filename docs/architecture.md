# Architecture

Loopy is a single Maven module organized into six layers. Each layer has a clear responsibility and communicates through well-defined interfaces.

```
io.github.markpollack.loopy
├── agent/    Agent loop, tool execution, skills, observability
├── tui/      Terminal UI (Elm Architecture)
├── command/  Slash command framework
├── forge/    Project scaffolding (forge-agent)
├── boot/     Spring Boot scaffolding commands
└── session/  Session persistence
```

## Layers

### Agent Layer (`agent/`)

The core agent loop, based on MiniAgent — a standalone agent implementation derived from [agent-harness](https://github.com/markpollack/agent-harness), built on Spring AI's `ChatClient` + `ToolCallAdvisor`, evolving independently within Loopy.

**Key components:**
- `MiniAgent` — Runs the think-act-observe loop: send messages to the LLM, receive tool calls, execute tools, feed results back
- `MiniAgentConfig` — Configuration (working directory, max turns, system prompt, timeouts)
- `AgentLoopAdvisor` — Spring AI advisor that wraps the tool-calling loop with observability and cost tracking
- `BashTool` — Executes shell commands in the agent's working directory
- `SkillsTool` — Progressive skill discovery: shows skill names/descriptions, loads full content on demand
- `ConsoleToolCallListener` — Prints tool call activity to stderr (for print/REPL modes)

**Skills discovery:** MiniAgent scans three sources for `SKILL.md` files — project (`.claude/skills/`), global (`~/.claude/skills/`), and classpath (`META-INF/skills/` in JARs). Skills use progressive disclosure to minimize token usage.

**Design choice:** MiniAgent is embedded (vendored), not a Maven dependency. This avoids pulling in transitive dependencies from agent-harness and lets Loopy's agent evolve independently. ArchUnit rules in `ArchitectureTest` enforce that no upstream imports leak in.

### TUI Layer (`tui/`)

Terminal UI built on [tui4j](https://github.com/williamcallahan/tui4j), a Java port of Bubble Tea's Elm Architecture.

**Key components:**
- `ChatScreen` — Main screen with chat history, text input, and agent interaction
- `ChatEntry` — Immutable record representing a message (USER, ASSISTANT, or SYSTEM role)
- `LogoScreen` — Splash screen shown on startup, transitions to ChatScreen

**Async pattern:** Agent calls run on a background thread via tui4j's `Command` abstraction. A `waiting` flag gates the Enter key to prevent overlapping requests. The spinner animates while waiting. User messages appear in the history immediately; assistant responses appear when the agent completes.

### Command Layer (`command/`)

Slash command framework for deterministic operations that don't need the LLM.

**Key components:**
- `SlashCommand` — Interface with `name()`, `execute(args, context)`, and `description()`
- `SlashCommandRegistry` — Maps `/name` to command instances, handles dispatch
- `CommandContext` — Carries working directory and session-clear callback
- Built-in commands: `HelpCommand`, `ClearCommand`, `QuitCommand`, `SkillsCommand`

**Interception point:** `ChatScreen.submitInput()` checks for `/` prefix and dispatches to the registry before the input reaches MiniAgent. This is deterministic — no LLM tokens consumed for slash commands.

### Forge Layer (`forge/`)

Project scaffolding for agent experiment projects.

**Key components:**
- `ForgeAgentCommand` — Slash command implementing `/forge-agent`
- `ExperimentBrief` — Parses YAML brief files defining experiment variants and datasets
- `TemplateCloner` — Clones a template repository and reinitializes git
- `TemplateCustomizer` — 7-step deterministic customization (package rename, POM updates, config generation, README generation)
- `CustomizationPromptBuilder` — Builds LLM prompts for variant descriptions
- `KBBootstrapPromptBuilder` — Templates for knowledge base reference harvesting

**Design choice:** Forge classes are copied from [markpollack/forge](https://github.com/markpollack/forge), not depended on via Maven. Same rationale as MiniAgent — avoids transitive dependencies and allows independent evolution.

### Boot Layer (`boot/`)

Spring Boot project scaffolding commands. All POM mutations are deterministic (Maven object model — no AI-generated XML). LLM is used only for natural language intent routing and preference extraction.

**Key components:**
- `BootNewCommand` — `/boot-new`: scaffold from bundled templates (4 templates, ScaffoldGraph, JavaParserRefactor)
- `BootSetupCommand` — `/boot-setup`: one-time preferences wizard (groupId, Java version, deps, database) saved to `~/.config/loopy/boot/preferences.yml`
- `BootAddCommand` — `/boot-add`: analyze project structure (SAE analysis → `PROJECT-ANALYSIS.md`), add Agent Starter dependency
- `BootModifyCommand` — `/boot-modify`: 11 deterministic recipes routed via LLM intent classification
- `StartersCommand` — `/starters`: Agent Starter catalog discovery and `pom.xml`-based suggestions

### Session Layer (`session/`)

Conversation persistence across Loopy restarts.

**Key components:**
- `SessionStore` — Serializes Spring AI `Message` history to JSON files in `~/.config/loopy/sessions/{timestamp}-{slug}.json`. Methods: `save()`, `load()`, `list()`.
- `SessionCommand` — `/session save|list|load` slash command. Auto-save fires on TUI exit via `/quit`.

## Data Flow

```
User Input
    │
    ├── starts with "/" ──► SlashCommandRegistry ──► Command handler ──► Output
    │
    └── regular text ──► MiniAgent.run(text)
                              │
                              ├── Build messages (system prompt + history + user input)
                              │
                              ├── Send to LLM provider (ChatModel — Anthropic, OpenAI, or Gemini)
                              │
                              ├── Receive response
                              │   ├── Text only ──► Return result
                              │   └── Tool calls ──► Execute tools ──► Feed results back ──► Loop
                              │
                              └── Check limits (max turns, cost, timeout)
                                  └── Return LoopyResult (status, output, metrics)
```

## Quality Guardrails

- **ArchUnit** — 6 architecture rules enforcing layer isolation and vendoring boundaries
- **JaCoCo** — Code coverage reporting
- **Spring JavaFormat** — Auto-applied on compile (tab indentation, Spring brace style)
- **JSpecify** — `@NullMarked` on all packages, `@Nullable` for explicit exceptions
- **Maven Enforcer** — Java 21+ required
- **OWASP Dependency-Check** — Behind `-P owasp` profile
