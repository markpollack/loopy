# Design: Loopy

> **Created**: 2026-03-01T15:00-05:00
> **Last updated**: 2026-03-13
> **Vision version**: 2026-03-08

## Overview

Loopy is a Spring Boot 4.0.3 CLI (no web server) that embeds its own copy of MiniAgent — the first "field agent" graduated from the agent-harness nursery. `LoopyApp` is `@SpringBootApplication` + `CommandLineRunner` — boots Spring context, gets auto-configured `ChatModel`, passes it to `LoopyCommand` (plain Picocli `@Command`, not a Spring bean).

Three execution modes: TUI (default, full terminal UI), REPL (readline loop), Print (`-p`, single-shot). All share the same MiniAgent, slash command registry, and skills infrastructure.

Skills are first-class: SkillsTool discovers domain knowledge from three sources (project, global, classpath) via progressive disclosure. Agent Starters extend skills with deterministic project analysis (SAE) and auto-configuration for the Spring community.

## Build Coordinates

| Field | Value |
|-------|-------|
| **Group ID** | `io.github.markpollack` |
| **Artifact ID** | `loopy` |
| **Version** | `0.1.0-SNAPSHOT` |
| **Packaging** | `jar` (single module, spring-boot-maven-plugin for fat JAR) |
| **Java version** | 21 |
| **Base package** | `io.github.markpollack.loopy` |
| **Framework** | Spring Boot 4.0.3 / Spring AI 2.0-M2 |

### Package Structure

```
io.github.markpollack.loopy/
├── LoopyApp.java              # @SpringBootApplication + CommandLineRunner
├── LoopyCommand.java          # Picocli @Command — mode dispatch, agent creation
├── agent/                     # MiniAgent — embedded field agent
│   ├── MiniAgent.java         # Core: ChatClient + AgentLoopAdvisor + tools
│   ├── BashTool.java          # Shell execution tool
│   ├── CompositeToolCallListener.java   # Multiplexes tool call events
│   ├── DebugToolCallListener.java       # --debug stderr output (tool names, args, duration)
│   ├── DebugLoopListener.java           # --debug stderr output (turn numbers, cost)
│   ├── callback/
│   │   └── AgentCallback.java           # Event callback interface
│   ├── config/
│   │   ├── AgentYaml.java               # agent.yaml binding record
│   │   └── AgentYamlLoader.java         # Loads agent.yaml (project + global)
│   ├── core/
│   │   ├── ToolCallListener.java        # Observability interface
│   │   ├── LoopState.java               # Immutable state (turns, tokens, cost, stuck detection)
│   │   └── TerminationReason.java       # Enum: MAX_TURNS, TIMEOUT, COST_LIMIT, STUCK, etc.
│   ├── journal/                         # Agent journal (structured observability)
│   │   └── ...
│   └── loop/
│       ├── AgentLoopAdvisor.java        # Main advisor (extends ToolCallAdvisor)
│       ├── AgentLoopConfig.java         # Loop config (maxTurns, timeout, costLimit)
│       ├── AgentLoopListener.java       # Loop event listener interface
│       └── AgentLoopTerminatedException.java
├── tui/
│   ├── ChatScreen.java        # Elm Architecture Model (tui4j)
│   └── ChatEntry.java         # Immutable chat history record (USER/ASSISTANT/SYSTEM)
├── boot/
│   ├── BootNewCommand.java    # /boot-new — scaffold new Spring Boot project
│   ├── BootModifyCommand.java # /boot-modify — structural POM modifications
│   ├── BootAddCommand.java    # /boot-add — bootstrap Agent Starter into existing project
│   ├── StartersCommand.java   # /starters — discover Agent Starters
│   ├── BootSetupCommand.java  # /boot-setup — one-time preferences wizard
│   ├── ScaffoldGraph.java     # Harness-patterns graph for boot-new pipeline
│   ├── BootNewTool.java       # Tool wrapper for boot-new (profile: boot)
│   └── BootModifyTool.java    # Tool wrapper for boot-modify (profile: boot)
├── command/
│   ├── SlashCommand.java      # Interface: name(), description(), execute(args, context)
│   ├── SlashCommandRegistry.java  # Maps "/name" → SlashCommand, dispatches
│   ├── HelpCommand.java       # /help — lists available commands
│   ├── ClearCommand.java      # /clear — clears agent session
│   ├── QuitCommand.java       # /quit — exits Loopy
│   ├── BtwCommand.java        # /btw — stateless side question (no session context)
│   ├── ModelCommand.java      # /model — show or change active model
│   ├── SessionCommand.java    # /session — save, list, load sessions
│   ├── SkillsCommand.java     # /skills — list, info, search, add, remove
│   └── SkillsCatalog.java     # Curated catalog (skills-catalog.json from classpath)
├── session/
│   └── SessionStore.java      # JSON session persistence (~/.config/loopy/sessions/)
├── tools/
│   ├── LoopyToolsFactory.java      # Builds tool lists by profile name
│   ├── ToolFactoryContext.java     # Context passed to ToolProfileContributor
│   └── ToolProfileContributor.java # Java ServiceLoader SPI for custom tool JARs
└── forge/
    ├── ForgeAgentCommand.java # /forge-agent — scaffold experiment project from brief
    ├── ExperimentBrief.java   # YAML brief parser
    ├── TemplateCloner.java    # Git clone + cleanup
    └── TemplateCustomizer.java # 7-step deterministic customization
```

### Key Dependencies

| Dependency | Scope | Purpose |
|------------|-------|---------|
| `spring-ai-starter-model-anthropic` | compile | Anthropic auto-config (default provider) |
| `spring-ai-starter-model-openai` | compile | OpenAI auto-config |
| `spring-ai-starter-model-google-genai` | compile | Google Gemini auto-config |
| `spring-ai-agent-utils:0.5.0-SNAPSHOT` | compile | Community tools (FileSystemTools, GlobTool, GrepTool, etc.) + SkillsTool |
| `tui4j:0.3.3-SNAPSHOT` | compile | Terminal UI (Bubble Tea port). Built from `~/projects/tui4j-research/tui4j/` |
| `picocli:4.7.6` | compile | CLI argument parsing |
| `snakeyaml:2.2` | compile | YAML brief parsing (forge) |

**Embedded (not Maven deps)** — copied into `io.github.markpollack.loopy.agent`:
- MiniAgent + AgentLoopAdvisor + BashTool + observability (~13 classes from agent-harness)
- These are Loopy's "field agent" — graduated from the nursery, evolving independently

## Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────────────┐
│                     LoopyApp / LoopyCommand               │
│  (Spring Boot CLI, Picocli @Command, mode dispatch)       │
│  ┌─────────┐  ┌──────────┐  ┌───────────┐                │
│  │TUI mode │  │REPL mode │  │Print mode │                │
│  │(default)│  │(--repl)  │  │(-p)       │                │
│  └────┬────┘  └────┬─────┘  └─────┬─────┘                │
└───────┼─────────────┼──────────────┼──────────────────────┘
        │             │              │
        ▼             ▼              ▼
┌──────────────────────────────────────────────────────────┐
│  Input Routing                                            │
│                                                           │
│  user input ──► starts with "/"? ──► YES ──►             │
│       │                                │                  │
│       │ NO                             ▼                  │
│       ▼                    SlashCommandRegistry            │
│   agentFunction            ┌───────────────────┐          │
│       │                    │ /help              │          │
│       │                    │ /clear             │          │
│       │                    │ /quit              │          │
│       │                    │ /skills            │          │
│       │                    │ /forge-agent       │          │
│       │                    └───────────────────┘          │
│       │                             │                     │
│       ▼                             ▼                     │
│   ┌─────────────────────────────────────────────┐        │
│   │             MiniAgent                        │        │
│   │  AgentLoopAdvisor ──► ChatClient             │        │
│   │  LoopState (turns, tokens, cost)             │        │
│   │  DebugToolCallListener, DebugLoopListener    │        │
│   │                                              │        │
│   │  Tools (profile-filtered via agent.yaml):    │        │
│   │    dev: bash, FileSystem, Glob, Grep,        │        │
│   │         ListDir, TodoWrite, AskUser          │        │
│   │    boot: BootNew, BootModify                 │        │
│   │    + TaskTool, SkillsTool (always)           │        │
│   │    + custom via ToolProfileContributor SPI   │        │
│   │                                              │        │
│   │  Provider (one active per session):          │        │
│   │    Anthropic │ OpenAI │ Google Gemini         │        │
│   └─────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────┘
```

### Skills Architecture

Skills are first-class in Loopy, independent of Spring. They follow the agentskills.io spec.

```
Discovery (three sources):
  .claude/skills/*/SKILL.md         ← project (team-shared, checked into repo)
  ~/.claude/skills/*/SKILL.md       ← global (personal, /skills add)
  META-INF/skills/ in JARs          ← classpath (Maven deps, SkillsJars)

Loading (SkillsTool from spring-ai-agent-utils):
  Progressive disclosure — agent sees skill name + description
  Agent calls tool by name to load full content (only when relevant)
  No tokens wasted on unused skills

Management (/skills command):
  /skills list       ← show discovered skills from all sources
  /skills search     ← search curated catalog (skills-catalog.json)
  /skills info       ← show skill details + install paths
  /skills add        ← download from catalog to ~/.claude/skills/
  /skills remove     ← uninstall from ~/.claude/skills/
```

**SkillsTool wiring**: `MiniAgent.builder().addSkillsDirectory(path)` and `.addSkillsResource(pattern)` register sources. SkillsTool.builder() returns a `ToolCallback` that goes in `directCallbacks`. Graceful degradation: no skills = no SkillsTool registered.

**Curated catalog**: `skills-catalog.json` baked into JAR. 23 skills from 8 publishers. `SkillsCatalog` loads from classpath, search by name/tag/description/author (case-insensitive substring).

### Agent Starters Architecture (DEFERRED — needs more thought)

> The Agent Starters concept (skills + SAE + auto-configuration for Spring projects) is documented in `plans/roadmap-skills.md` Stages 4–5 but is **not currently planned for implementation**. Experimental evidence suggests skills provide limited improvement for software development tasks (consistent with the SkillsBench research paper). The SAE (project analysis) component may still have standalone value independent of the skills layer. Revisit once the modular platform (DD-12–DD-17) is complete and there is a clearer picture of where deterministic project context injection fits.

### Multi-Provider Architecture

All three provider starters on the classpath. `--provider` flag sets `spring.ai.model.chat` system property before Spring context boots, activating exactly one `ChatModel` bean.

| Provider | Flag | Default model | Compaction model |
|----------|------|--------------|-----------------|
| Anthropic | `--provider anthropic` (default) | claude-sonnet-4-20250514 | claude-haiku-4-5-20251001 |
| OpenAI | `--provider openai` | gpt-4o | gpt-4o-mini |
| Google Gemini | `--provider google-genai` | gemini-2.5-flash | gemini-2.5-flash-lite |

Compaction uses the same `ChatModel` bean with a per-request model override via `ChatOptions.builder().model(cheapModel)`. No separate credentials needed.

### Async Message Flow (TUI Mode)

LLM calls run on a background thread via tui4j's `Command` (a `Supplier<Message>` thunk). The returned `Message` is delivered back to `update()` on the main thread.

```
User presses Enter with text
  │
  ├─ starts with "/" ──► SlashCommandRegistry.dispatch()
  │                        ├─ known command ──► execute ──► SYSTEM entry in history
  │                        └─ unknown ──► error message
  │
  └─ regular text ──► submitToAgent(text)
                        │
                        ▼
                    1. Add USER entry to history
                    2. Set waiting = true, start Spinner
                    3. Return batch(agentCall(), spinner.init())
                        │
                        ▼  (background thread)
                    MiniAgent.run(text) → AgentReplyMessage(response)
                        │
                        ▼  (back on main thread)
                    update(AgentReplyMessage)
                        1. Add ASSISTANT entry to history
                        2. Set waiting = false
```

**Waiting gate**: When `waiting == true`, Enter key is ignored (prevents duplicate submissions).

## Interfaces

### SlashCommand

```java
public interface SlashCommand {
    String name();          // e.g., "skills" — without leading "/"
    String description();   // e.g., "Manage agent skills"
    String execute(String args, CommandContext context);

    default ContextType contextType() { return ContextType.NONE; }
    default boolean requiresArguments() { return false; }

    enum ContextType { NONE, SYSTEM, ASSISTANT }
}
```

**Contract**:
- `execute()` receives everything after the command name as `args` (trimmed)
- `execute()` returns a human-readable result string
- `execute()` must not throw — catch exceptions and return error messages
- `contextType()` controls output handling: `NONE` = display only, `SYSTEM` = added to LLM context

### CommandContext

```java
public record CommandContext(
    Path workingDirectory,
    Runnable clearSession,
    Consumer<String> setModel,
    Function<String, String> agentDelegate
) {}
```

## Design Decisions

> **Active** — decisions under current development or discussion.
> **Completed** — implemented and stable, kept for historical context.
> **Deferred** — acknowledged but not currently planned.

---

## Active Design Decisions

### DD-1: Single Repo, Multiple Maven Modules As Needed

**Decision**: Single repository. Maven modules introduced as separation becomes load-bearing — not speculatively. ArchUnit rules updated per module boundary added. No new repos until a module has an independent consumer.

**Current modules**: `loopy` (root, single module — all code today)
**Planned splits** (as protocol/tool work demands):
- `loopy-runtime` — MiniAgent, AgentLoopAdvisor, core loop (no TUI, no slash commands)
- `loopy-tools-core` — bash, file, grep, glob, list-directory (wrapping spring-ai-agent-utils)
- `loopy-tools-boot` — Spring Boot scaffolding tools (`/boot-new`, `/boot-add`, `/boot-modify`, `/starters`)
- `loopy-cli` — TUI, REPL, slash command registry, all `/commands` (depends on runtime + tools)

### DD-12: Tool Plugin SPI via Spring Auto-Configuration

**Decision**: Tools are discovered from the classpath via Spring auto-configuration, not hardcoded in `MiniAgent.java`. Each tool module provides an `AutoConfiguration` class that contributes `ToolCallback` beans. MiniAgent collects all `ToolCallback` beans from the context.

**Mechanism**:
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  → LoopyCoreToolsAutoConfiguration   (registers bash, file, grep, glob, list-directory)
  → LoopyBootToolsAutoConfiguration   (registers boot-new, boot-modify, starters, etc.)
  → (future) LoopyGitToolsAutoConfiguration
```

`MiniAgent` collects `Map<String, ToolCallback>` from the application context — any bean of type `ToolCallback` is automatically included. No registration code required when adding a new tool module.

**Profile filtering** (see DD-16): all tool `ToolCallback` beans are registered by auto-configuration unconditionally. Profile filtering happens at MiniAgent construction time — only beans matching the active profiles are passed to the agent. This avoids pre-boot complexity; `agent.yaml` is loaded after the Spring context starts.

**Rationale**: This is exactly how Spring Boot starters work. Add dependency → beans appear → agent gains capability. Zero code for the user. Works with SkillsJars (classpath skills), MCP tool callbacks, and A2A remote-agent tools by the same mechanism.

**Current state (Stage 8a/8b — SPI-first validation)**: `LoopyToolsFactory` organizes tool construction by profile name. `ToolProfileContributor` (Java `ServiceLoader` SPI) enables custom tool JARs via classpath — declare in `pom.xml`, implement the interface, register via `META-INF/services`. This validates the profile filtering design before Maven module extraction.

**Eventual target (Maven module extraction)**: each profile becomes its own Maven module with a Spring `AutoConfiguration` class. `loopy-tools-dev`, `loopy-tools-boot` etc. as published artifacts. Profile filtering moves from `LoopyToolsFactory` to Spring bean collection at MiniAgent construction time. Same agent.yaml contract, different delivery mechanism.

---

### DD-13: `agent.yaml` — Declarative Agent Configuration

**Decision**: A single `agent.yaml` file (project-local or `~/.config/loopy/agent.yaml` for global) is the top-level declarative contract for a Loopy agent. It composes profiles, references MCP config, declares A2A client connections, specifies SkillsJar dependencies, and sets identity for A2A server mode.

**Schema**:

```yaml
# agent.yaml — zero Java required for common cases

agent:
  name: my-pr-agent                # Identity — used for A2A AgentCard name
  description: PR review agent for Java projects
  version: 1.0.0

tools:
  profiles:
    - dev       # bash, file-system, grep, glob, list-directory, ask-user-question
    - boot      # Spring Boot scaffolding (boot-new, boot-modify, starters, boot-add)
  # Optional explicit module includes (for custom tool JARs not in a named profile):
  # modules:
  #   - com.example:my-custom-tools:1.0.0

skills:
  # By SkillsJars Maven coordinates:
  # - groupId: com.skillsjars
  #   artifactId: obra__superpowers__systematic-debugging
  # By catalog name (resolved via skills-catalog.json):
  # - name: systematic-debugging

mcp:
  config: .mcp.json         # Standard Claude Code / Cursor compatible file
                            # Also accepts: ~/.claude.json (user-global)

a2a:
  client:
    defaults:
      timeout-seconds: 60
      accepted-output-modes: [text]
    agents:
      code-reviewer:
        url: http://localhost:10001/review/
      test-runner:
        url: http://localhost:10002/test/
        timeout-seconds: 120        # Override default
  server:
    enabled: false          # Expose this agent as an A2A server
                            # When true: agent.name/description populate the AgentCard

runtime:
  max-turns: 20
  cost-limit-dollars: 5.00
  # model: claude-sonnet-4-20250514   # Override provider default
```

**What goes in `agent.yaml` (complete list)**:
- **Identity** (`agent.name/description/version`) — used for A2A AgentCard when server mode is on
- **Tool profiles** — named capability bundles controlling which tool auto-configurations activate
- **MCP reference** — path to `.mcp.json` (standard format, never re-defined here)
- **A2A client connections** — the missing declarative layer (no standard exists yet; this is Loopy's contribution)
- **A2A server toggle** — opt-in exposure as an A2A agent
- **Skills** — SkillsJars by Maven coordinates or catalog name
- **Runtime overrides** — max-turns, cost-limit, model override

**What stays out of `agent.yaml`**: AgentCard skills/capabilities (those are code-defined via `@Bean` when building a custom A2A server agent), system prompt content (goes in CLAUDE.md / AGENTS.md), LLM provider credentials (stay in `application.yml` or env vars).

---

### DD-14: MCP Integration — Standard `.mcp.json` Format

**Decision**: Loopy reads `.mcp.json` from the project root (or `~/.claude.json` for user-global), using the exact schema adopted by Claude Code, Cursor, Windsurf, and Zed. No Loopy-specific wrapper. Users with an existing `.mcp.json` for Claude Code get Loopy MCP support automatically.

**Format** (cross-tool standard):
```json
{
  "mcpServers": {
    "brave-search": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": { "BRAVE_API_KEY": "${BRAVE_API_KEY}" }
    },
    "filesystem": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects"]
    }
  }
}
```

**Discovery order**: `.mcp.json` (project root) → `~/.claude.json` (user-global) → path in `agent.yaml` `mcp.config` field. Project-level overrides global. MCP server tools are registered alongside built-in `ToolCallback` beans — the agent sees them as peers.

**`agent.yaml` only references** the MCP config file by path. MCP server definitions are never duplicated or nested inside `agent.yaml`.

---

### DD-15: A2A Client Auto-Configuration (Declarative + Upstream Contribution)

**Decision**: Implement `A2AClientAutoConfiguration` and `A2AClientProperties` (`spring.ai.a2a.client.*`) that translate declarative YAML into live A2A `Client` instances, registered as `ToolCallback` beans so remote A2A agents appear as tools to MiniAgent.

**The gap**: `spring-ai-a2a` provides server-side auto-config but no client-side equivalent. The examples use ad-hoc `RemoteAgentConnections` with a plain property list. Loopy fills this gap and can contribute it upstream to `spring-ai-a2a`.

**Translation** (YAML → Java at startup):
```yaml
a2a:
  client:
    agents:
      code-reviewer:
        url: http://localhost:10001/review/
        timeout-seconds: 60
```
→
```java
// Auto-configured — no user code needed:
AgentCard card = A2A.getAgentCard(url, url + ".well-known/agent-card.json", null);
ClientConfig config = new ClientConfig.Builder()
    .setAcceptedOutputModes(List.of("text"))
    .build();
Client client = Client.builder(card)
    .clientConfig(config)
    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
    .build();
// Wrapped as ToolCallback named "code-reviewer", registered with MiniAgent
```

**`A2AClientProperties`** (`spring.ai.a2a.client`):
- `enabled: boolean` (default: false — only if `a2a.client` block present)
- `defaults.timeout-seconds`, `defaults.accepted-output-modes`
- `agents: Map<String, AgentConnectionProperties>` (name → url, timeout, modes)

**Upstream path**: Contribute `spring-ai-a2a-client-autoconfigure` to `spring-ai-community/spring-ai-a2a`. The module fills the symmetric gap opposite the existing server autoconfigure.

**Upstream contribution timing**: Do not contribute `A2AClientAutoConfiguration` upstream to `spring-ai-a2a` until Loopy controls that repo or the timing is right. Implement in Loopy first, validate the design, then propose.

---

### DD-16: Profile Bundles — Named Tool Capability Sets

**Decision**: Named profiles control which tool auto-configurations activate. A profile is a named set of `ToolCallback` beans. Declared in `agent.yaml` `tools.profiles`. Multiple profiles compose additively.

**Built-in profiles**:

| Profile | Tools included | Default in Loopy |
|---------|---------------|-----------------|
| `dev` | bash, file-system, grep, glob, list-directory, ask-user-question, todo-write, task/subagents, skills | YES |
| `boot` | boot-new, boot-modify, boot-add, boot-setup, starters | YES (Loopy's identity) |
| `headless` | same as `dev` minus ask-user-question | for CI/CD batch agents |
| `readonly` | file-system, grep, glob, list-directory only — no bash, no shell, no todo-write | for secure/restricted environments |

**Activation**: `agent.yaml` is read after the Spring context boots. Active profiles are parsed, then MiniAgent filters the full set of `ToolCallback` beans from the context at construction time — no `@ConditionalOnProperty`, no pre-boot property setting needed.

```yaml
# agent.yaml — declares which profiles are active
tools:
  profiles:
    - dev
    - boot
```

```java
// MiniAgent construction — runtime filter (not auto-config condition):
List<ToolCallback> activeTools = allToolCallbacks.stream()
    .filter(tool -> activeProfiles.stream().anyMatch(p -> tool.profileTag().equals(p)))
    .toList();
```

**Loopy CLI default** (no `agent.yaml`): `dev` + `boot` profiles active. Both are baked into Loopy's identity and need no explicit declaration for existing users.

---

### DD-17: TUI as Optional Layer — Headless Runtime

**Decision**: `loopy-runtime` (MiniAgent + tool auto-config) runs without the TUI. The TUI is a `loopy-cli` concern layered on top. The same runtime runs in CI/CD, in Docker, as an A2A server, or as a batch job — without any TUI dependency.

**"Dev mode"**: The interactive TUI chat becomes the developer's inspection/debugging interface for any agent built on the Loopy runtime. A dedicated autonomous agent (PR review bot, incident responder) runs headless in production; a developer opens it in TUI mode to inspect, test, and iterate.

**Deployment shapes**:

```
Interactive dev:   loopy-cli  →  loopy-runtime  →  tools + protocols
Batch/CI:          loopy -p   →  loopy-runtime  →  tools + protocols
A2A server:        ACP/A2A listener  →  loopy-runtime  →  tools + protocols
Custom agent JAR:  customer's main()  →  loopy-runtime  →  their tool modules
```

The split is a module boundary in `pom.xml`, not a different repository. `loopy-cli` depends on `loopy-runtime`; the reverse is never true.

---

### DD-18: `/forge-agent` Evolution — Declarative Agent Artifacts (Deferred)

**Decision**: Deferred until modular tool architecture (DD-12 + DD-16) is complete. At that point `/forge-agent` can produce a fully configured agent by generating an `agent.yaml` + declaring tool profile dependencies in `pom.xml` — zero Java changes needed for common cases.

**Current state**: Produces a compilable Spring Boot experiment project skeleton. The gap is that customization still requires editing Java code. With `agent.yaml` as the declaration layer, the forge output becomes: `agent.yaml` (identity, profiles, MCP, A2A) + `pom.xml` (tool module dependencies) + `application.yml` (provider/credentials) + AGENTS.md (domain instructions). The agent is fully operational with no Java authoring.

**Prerequisite**: DD-12 (tool auto-config), DD-13 (agent.yaml schema), DD-15 (A2A client auto-config).

### DD-19: ACP Integration — Loopy as an Editor-Driven Agent

**Decision**: Integrate ACP (Agent Client Protocol) so editors (Zed, JetBrains, VS Code) can drive Loopy interactively. ACP is the right transport for Loopy's CLI/subprocess model — editors spawn Loopy as a subprocess over stdio, or connect over WebSocket. The integration is annotation-based with no Spring dependency, estimated ~3-6 hours.

**Why ACP over A2A for editor integration**: A2A uses HTTP/JSON-RPC (web-tier model) — requires a running HTTP server. ACP uses stdio (subprocess) or WebSocket — the editor spawns Loopy as a process. This maps directly to how Claude Code, Cursor, and Zed already work. ACP gives the "bigger story": users can chat with Loopy from their IDE without leaving the editor.

**ACP SDK characteristics** (from investigation of `~/acp/acp-java/`):
- Pure Java 17+, no Spring dependency
- Annotation-based: `@AcpAgent`, `@Initialize`, `@NewSession`, `@Prompt` — ~25 lines for a basic agent
- Transports: `stdio` (subprocess, default) or WebSocket (Jetty)
- Modules: `acp-core`, `acp-annotations`, `acp-agent-support`, `acp-websocket-jetty`, `acp-test`
- No Spring Boot autoconfigure exists — not needed for Loopy

**Integration approach**: Wrap MiniAgent in an `@AcpAgent` class. `@Prompt` handler feeds the message to `MiniAgent.run()` and streams output back. Wired alongside the existing TUI/REPL/print modes in `LoopyCommand`. When `--acp` flag is present, start the ACP listener instead of TUI.

**Interaction with DD-17 (Headless Runtime)**: ACP mode is another headless deployment shape — same runtime, different transport. No TUI, no REPL, just the MiniAgent loop responding to ACP messages.

**Ordering**: Implement after MCP (DD-14) and Modular Foundation (DD-12/16). ACP and A2A (DD-15) can proceed in parallel once the tool plugin SPI is in place — they are both protocol adapters over the same runtime.

---

## `agent.yaml` Full Reference

See DD-13 for schema. All files are loaded **after the Spring context boots** — no pre-boot complexity. Loading order (later entries take lower precedence):
1. `./agent.yaml` (project-local, highest precedence)
2. `~/.config/loopy/agent.yaml` (user-global)
3. Loopy built-in defaults (`dev` + `boot` profiles active, no MCP, no A2A)

---

## Completed Design Decisions

Implemented and stable. Kept for historical context.

### DD-2: Slash Commands Intercepted Before Agent

**Decision**: Intercept in `ChatScreen.submitInput()` before calling MiniAgent. Slash commands are deterministic — no LLM needed. Intercepting early avoids wasted API calls and is 100% reliable.

### DD-3: Copy Forge Code (Not Depend on Forge JAR)

**Decision**: Copy 3 forge classes into `io.github.markpollack.loopy.forge`. Avoids transitive agent-client/claude-agent dependencies.

### DD-4: Embed MiniAgent (Copy, Not Depend)

**Decision**: Copy ~13 classes from agent-harness into `io.github.markpollack.loopy.agent`. Loopy owns its agent — the first "field agent" graduated from the nursery.

### DD-5: Async LLM via Command Thunks

**Decision**: Use tui4j's `Command` (`Supplier<Message>`) as background thread thunks. Thread-safe because Elm Architecture guarantees single-threaded `update()` calls.

### DD-6: TextInput for v1

**Decision**: Keep `TextInput` (single-line) for v1. Consider `Textarea` with `Ctrl+J` for newlines later.

### DD-7: Multi-Provider via Spring Auto-Config

**Decision**: All three provider starters on classpath. `--provider` flag sets `spring.ai.model.chat` system property before context boots, activating exactly one `ChatModel` bean. No conditional compilation, no profiles — one fat JAR serves all providers.

### DD-8: Skills as First-Class Citizens

**Decision**: Skills are independent of Spring and work in any agentic CLI. SkillsTool (from spring-ai-agent-utils) handles loading via progressive disclosure. Three discovery sources: project `.claude/skills/`, global `~/.claude/skills/`, classpath `META-INF/skills/`. Curated catalog baked into JAR for discovery.

---

## Deferred Design Decisions

Acknowledged but not currently planned. Revisit when prerequisites are met.

### DD-9: Agent Starters — Skills + SAE for Spring Projects

Experimental evidence (SkillsBench research paper) suggests skills provide limited improvement for software development tasks. The concept needs more thought before implementation. `ProjectAnalyzer` (SAE) may have standalone value independent of the skills layer — deferred until the modular platform is complete.

### DD-10: SAE as MiniAgent Pre-Step

Depends on DD-9 direction. Deferred.

### DD-11: Project-Aware Starter Suggestion

Depends on DD-9 direction. Deferred.

## Testing Strategy

- **Unit tests**: SlashCommand dispatch, ChatEntry, ExperimentBrief parsing, SkillsCatalog search, SkillsCommand subcommands
- **Architecture tests**: ArchUnit rules for layer isolation (agent ✗→ tui, command ✗→ agent, etc.)
- **Integration tests**: Multi-provider ChatModel creation, skills auto-discovery from classpath
- **Coverage**: JaCoCo (99 tests as of Stage 3 completion)

## Error Handling

- Slash commands catch all exceptions internally and return error strings
- MiniAgent errors handled by MiniAgent and returned in `AgentResult.output()`
- TUI-level errors terminate with non-zero exit code
- Missing API key caught at startup with clear error message
- Skills graceful degradation: no skills = no SkillsTool registered, no error

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-01T15:00-05:00 | Initial draft — TUI + MiniAgent + forge commands | Forge project bootstrap |
| 2026-03-01T17:00-05:00 | Fix DD-3; clarify CommandContext; decide CLAUDE.md auto-inject | Design review |
| 2026-03-01T18:00-05:00 | Fix DD-2 title; resolve OQ-1; add /quit; CLI research | Cross-industry CLI research |
| 2026-03-02 | Add DD-5 (async thunks), DD-6 (TextInput), async message flow | Brief app deep-dive |
| 2026-03-08 | Major update — add skills architecture (DD-8), Agent Starters (DD-9), SAE (DD-10), multi-provider (DD-7), updated package structure, component diagram | Strategic clarity before Stage 4 |
| 2026-03-13 | Modular platform design — DD-1 revised (multi-module), DD-12 (tool plugin SPI), DD-13 (agent.yaml schema), DD-14 (MCP .mcp.json), DD-15 (A2A client auto-config + upstream contribution), DD-16 (profile bundles), DD-17 (headless runtime), DD-18 (forge-agent evolution, deferred). | Loopy as agent forge platform |
| 2026-03-13 | Resolve open design questions: DD-12/16 corrected (runtime filter at MiniAgent construction, not @ConditionalOnProperty; agent.yaml loads after Spring context boots). Add DD-19: ACP integration (lightweight, annotation-based, stdio/WebSocket, ~3-6h, IDE editor transport). | Design question resolution |
| 2026-03-13 | Stage 8a/8b implementation: add ToolProfileContributor SPI to DD-12 (current SPI-first state vs eventual Spring AutoConfiguration target). Fix CommandContext (4 fields). Add tools/ and boot/ packages to package structure. Remove duplicate DD-18 from Deferred. Note DD-15 upstream timing. | Stage 8 implementation review |
