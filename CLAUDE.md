# Loopy

A loop-driven interactive coding agent CLI. Converges MiniAgent (standalone agent loop), tui4j (Bubble Tea TUI), and forge lifecycle commands into a single executable.

## Build

Requires Java 21+ (tui4j is compiled for Java 21). Use SDKMAN to switch if needed:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.9-librca ./mvnw test
```

```bash
./mvnw compile     # Compile (Spring JavaFormat auto-applies)
./mvnw test        # Run tests (ArchUnit + JaCoCo coverage)
./mvnw package     # Build fat JAR
./mvnw verify -P owasp  # CVE scan (optional, slow)
```

## Run

```bash
# Interactive TUI (default, Anthropic)
java -jar target/loopy-0.2.0-SNAPSHOT.jar

# Single-shot print mode
java -jar target/loopy-0.2.0-SNAPSHOT.jar -p "create a hello world app"

# REPL mode (for testing)
java -jar target/loopy-0.2.0-SNAPSHOT.jar --repl

# With provider selection
java -jar target/loopy-0.2.0-SNAPSHOT.jar --provider openai -p "hello"
java -jar target/loopy-0.2.0-SNAPSHOT.jar --provider google-genai --repl

# With options
java -jar target/loopy-0.2.0-SNAPSHOT.jar -d ~/projects/myapp -m claude-sonnet-4-20250514 -t 20
```

Requires API key for selected provider: `ANTHROPIC_API_KEY` (default), `OPENAI_API_KEY`, or `GOOGLE_API_KEY`. Available in `~/.env`.

## Implementation Progress

Core complete — Spring Boot 4.0.3 adoption, multi-provider (Anthropic/OpenAI/Gemini), cost visibility, context compaction, agent observability (journal-core, debug logging). Skills complete (SkillsTool wired, `/skills` command, curated catalog with 23 skills, search/add/remove). Agent quality hardened (Stage 7): system prompt rewrite, ListDirectory tool, AGENTS.md injection, graceful tool errors, grace turn on max-turns, tool-call stuck detection, session persistence.
**Source of truth**: `plans/ROADMAP.md` — main roadmap + index to feature roadmaps.
**Boot scaffolding roadmap**: `plans/roadmap-boot.md` — Wave 2 priority #1: `/boot-new`, `/boot-add`, `/starters`, `/boot-modify`
- `harness-patterns:0.9.0-SNAPSHOT` dep (not copy) — graph classes in `io.github.markpollack.harness.patterns.graph`
- `FunctionGraphNode` for all LLM nodes — MiniAgent evolved past AgentLoop, backport is future work
- `JavaParserRefactor` — deterministic package rename using JavaParser AST + `LexicalPreservingPrinter`; requires `JAVA_18` language level for record support
- MiniAgent grow cycle = Stage 7b (grow cycle: system prompt + mode-aware prompts via terminal-bench → experiment-driver)
**Skills roadmap**: `plans/roadmap-skills.md`
**Protocol stack roadmaps**: `plans/roadmap-mcp.md`, `plans/roadmap-acp.md`, `plans/roadmap-a2a.md`.

## Architecture

Spring Boot 4.0.3 CLI (no web server). `LoopyApp` is `@SpringBootApplication` + `CommandLineRunner` — boots Spring context, gets auto-configured `ChatModel`, passes it to `LoopyCommand` (plain Picocli `@Command`, not a Spring bean).

Single-module CLI with four layers:

- **Agent layer** (`agent/`) — MiniAgent (first field agent, copied from agent-harness), AgentLoopAdvisor, BashTool, observability — `io.github.markpollack.loopy.agent`
- **TUI layer** (`tui/`) — ChatScreen (Elm Architecture via tui4j), ChatEntry — `io.github.markpollack.loopy.tui`
- **Command layer** (`command/`) — SlashCommand interface, SlashCommandRegistry, HelpCommand, ClearCommand — `io.github.markpollack.loopy.command`
- **Forge layer** (`forge/`) — ExperimentBrief, TemplateCloner, TemplateCustomizer, ForgeAgentCommand — `io.github.markpollack.loopy.forge`
- **Boot layer** (`boot/`) — `/boot-new`, `/starters`, `/boot-add`, `/boot-modify` — Spring Boot scaffolding + SAE analysis (`BootProjectAnalyzer` → `PROJECT-ANALYSIS.md`) — `io.github.markpollack.loopy.boot`
- **Session layer** (`session/`) — `SessionStore` (JSON files in `~/.config/loopy/sessions/`), `SessionCommand` (`/session save|list|load`) — `io.github.markpollack.loopy.session`

User input starting with `/` is intercepted by the command layer before reaching MiniAgent. Everything else flows through MiniAgent's agent loop (think → tool-call → observe).

MiniAgent is embedded — ~13 classes copied from agent-harness, evolving independently as Loopy's own field agent.

### Slash Commands

| Command | Description |
|---------|-------------|
| `/help` | List available commands |
| `/clear` | Clear session memory |
| `/btw` | Ask a side question without interrupting session context — stateless single LLM call, nothing added to conversation history |
| `/quit` | Exit Loopy |
| `/skills` | Discover, search, install, remove domain skills |
| `/boot-new` | Scaffold a new Spring Boot project from a bundled template |
| `/boot-setup` | One-time preferences wizard: groupId, Java version, always-add deps (top-5 curated + free-form NL via `--more`), preferred database. Saved to `~/.config/loopy/boot/preferences.yml`. `/boot-new` redirects here on first use. |
| `/starters` | Discover Agent Starters; suggest by pom.xml triggers |
| `/boot-add` | Bootstrap domain capabilities (Agent Starter) into existing project |
| `/boot-modify` | Apply structural modifications (set java version, clean pom, add native support, etc.) |
| `/forge-agent` | Bootstrap an agent experiment project from a brief |
| `/session` | Save, list, or load sessions: `save [name]` / `list` / `load <id>`. Auto-saves on `/quit`. Sessions in `~/.config/loopy/sessions/`. |

### Key Design Decisions

- **Single module** — packages provide sufficient separation for v1
- **Slash commands intercepted in ChatScreen** — deterministic, no LLM tokens wasted
- **Async Command thunks** — ALL input (agent calls AND slash commands) runs on a background thread via tui4j `Command`; spinner animates; `waiting` gates Enter. Prevents long-running slash commands (LLM-backed `/boot-new`, `/btw`, etc.) from freezing the event loop.
- **`/btw` is stateless** — direct `ChatModel.call()`, no MiniAgent, no session memory. Answer appears in TUI history but is never added to the agent's conversation context.
- **Forge code copied (not depended on)** — avoids transitive agent-client/claude-agent deps
- **MiniAgent embedded (not depended on)** — first field agent, graduated from agent-harness nursery, evolves independently
- **Jackson 2.20+ required** — Jackson 3 (from Spring AI) introspects Jackson 2 annotations for backwards compat
- **Multi-provider via `spring.ai.model.chat`** — all three starters on classpath, `--provider` sets system property pre-boot to activate exactly one
- **Per-request compaction model** — same ChatModel, cheap model name via `ChatOptions.builder().model(name)` override. No separate API credentials needed.
- **Grace turn on max-turns** — when `AgentLoopAdvisor` throws `MAX_TURNS_REACHED`, `MiniAgent` makes one tool-free call asking for a summary. Returns `COMPLETED_WITH_WARNING` if successful, falls back to `TURN_LIMIT_REACHED`.
- **Tool-call stuck detection** — `ToolCallHashTracker` (SHA-256 per call) feeds `AgentLoopAdvisor`: triggers `STUCK_DETECTED` on 5 consecutive identical tool calls or A-B-A-B alternation over 10 turns. Complements existing output-hash check.
- **Case-insensitive tool resolver** — unknown tool names fall back to bash rather than crashing (handles smaller models capitalizing "Bash", "LS", etc.)
- **Session persistence** — `SessionStore` writes JSON to `~/.config/loopy/sessions/`. Includes message history (user/assistant/tool), metadata (model, provider, workdir). Auto-saves on TUI exit. `/session save|list|load` commands for manual control.
- **AGENTS.md + CLAUDE.md injection** — `LoopyCommand.buildContextInjection()` appends both files to the system prompt (AGENTS.md first, CLAUDE.md second). Root working directory only.

## Dependencies

- `tui4j` 0.3.3-SNAPSHOT — Terminal UI (Bubble Tea port for Java). Built from local source.
- `spring-ai-agent-utils` 0.5.0-SNAPSHOT — Community tools (FileSystemTools, GlobTool, GrepTool, ListDirectoryTool, AskUserQuestionTool, TodoWriteTool, TaskTool)
- `journal-core` 1.0.1-SNAPSHOT — Structured agent run/event tracking (`io.github.markpollack:journal-core`)
- `harness-patterns` 0.9.0-SNAPSHOT — Graph composition for boot scaffolding (`GraphCompositionStrategy`, `FunctionGraphNode`, `GraphContext`). Source: `~/projects/agent-harness/harness-patterns/`. Install: `cd ~/projects/agent-harness && JAVA_HOME=~/.sdkman/candidates/java/21.0.9-librca ./mvnw install -DskipTests`
- `javaparser-core` 3.28.0 — Deterministic package rename in `/boot-new`. Configured at `JAVA_25`. Supports Java 1.0–25. Must be declared explicitly — NOT transitive.
- `spring-ai-starter-model-anthropic` — Anthropic auto-config (Spring AI 2.0-M2 / Spring Boot 4.0.3)
- `spring-ai-starter-model-openai` — OpenAI auto-config
- `spring-ai-starter-model-google-genai` — Google Gemini auto-config
- `picocli` 4.7.6 — CLI argument parsing
- `snakeyaml` 2.2 — YAML brief parsing

### Source Code Preference

**Always prefer reading local source checkouts over inspecting JARs in `~/.m2`**. Almost the entire dependency tree is open source and checked out locally. JAR inspection (`jar xf`, decompiled classes) lacks Javadoc, comments, and surrounding context. Key source locations:

| Dependency | Local source |
|------------|-------------|
| Spring AI | `~/projects/spring-ai/` |
| tui4j | `~/projects/tui4j-research/tui4j/` |
| spring-ai-agent-utils | `~/community/spring-ai-agent-utils/` |
| agent-harness (MiniAgent origin) | `~/projects/agent-harness/` |
| agent-journal | `~/projects/agent-journal/` |
| agent-client (MCP design, adapters) | `~/community/agent-client/` |

Also check `~/community/`, `~/projects/`, `~/research/supporting_repos/` before decompiling.

### Protocol Repos (future extensibility)

| Protocol | Repo | Local source | Version |
|----------|------|-------------|---------|
| MCP (Model Context Protocol) | `modelcontextprotocol/java-sdk` | `~/mcp/java-sdk/` | v1.0.0 |
| MCP annotations | `spring-ai-community/mcp-annotations` | `~/community/mcp-annotations/` | — |
| ACP (Agent Client Protocol) | `agentclientprotocol/java-sdk` | `~/acp/acp-java/` | v0.9.0-SNAPSHOT |
| ACP docs | — | `~/community/mintlify-docs/acp-java-sdk/` | — |
| A2A (Agent-to-Agent) | `spring-ai-community/spring-ai-a2a` | `~/community/spring-ai-a2a/` | v0.2.0 |
| Spring AI (MCP starters) | `spring-projects/spring-ai` | `~/projects/spring-ai/` | 2.0.0-M2 |

### Competing CLI Repos (for reference)

| CLI | Local source |
|-----|-------------|
| Goose | `~/research/supporting_repos/goose/` |
| Gemini CLI | `~/research/supporting_repos/gemini-cli/` |
| Codex CLI | `~/research/supporting_repos/openai-codex-cli/` |
| Spring CLI | `~/projects/spring-cli/` |

**Embedded (not Maven deps)**: MiniAgent + ~13 agent-harness classes in `io.github.markpollack.loopy.agent` — Loopy's own field agent

## Quality

- **JaCoCo** 0.8.11 — coverage at `target/site/jacoco/index.html`
- **ArchUnit** 1.4.1 — 6 architecture rules in `ArchitectureTest.java` (layer isolation + vendoring guardrails)
- **JSpecify** 1.0.0 — `@NullMarked` on all packages; use `@Nullable` for exceptions
- **Spring JavaFormat** 0.0.43 — auto-applies on compile (tab indentation, Spring brace style)
- **Maven Enforcer** 3.5.0 — Java 21+ required
- **OWASP Dep-Check** 12.1.0 — behind `-P owasp` profile

## TUI4J Reference Material

See the tui4j-research KB at `~/projects/tui4j-research/` — read its `CLAUDE.md` for routing tables, component catalog, API changelog, and build instructions.

## Thesis Context — Knowledge-Directed Execution

Loopy is the **product that makes the equation tangible**:

> `knowledge + structured execution > model`

Curated domain knowledge + disciplined execution process contribute more to agent quality than model choice. Loopy lets anyone build and iterate on knowledge-directed agents for their domain.

### How the Equation Maps to Loopy

| Equation term | Loopy feature |
|---------------|---------------|
| **Knowledge** (curated opinions in navigable files) | `/forge-agent` starter KB bootstrapping, `/knowledge` commands (future) |
| **Structured execution** (checkpoints, scoped context, tiered validation) | `/run` + `/grow` pipeline, CascadedJury integration (future) |
| **> model** (iterate cheap levers first) | Diagnostic feedback tells you WHICH lever to fix |

### Slash Command Family

The `forge-` prefix is a **namespace for bootstrapping operations** — consistent with existing `~/.claude/commands/forge-*.md` family (`forge-project`, `forge-kb`, `forge-research`, `forge-steward`). Pattern: `forge-{thing-being-created}`.

**v1** (implemented): `/forge-agent`, `/skills`, `/help`, `/clear`, `/quit`

**Future** (requires experiment-driver integration):

| Command | What it does |
|---------|-------------|
| `/run` | Execute agent against dataset, measure correctness + efficiency |
| `/grow` | Iterate: run → judge → identify gap → fix → run again |
| `/judge` | Evaluate a completed run against the jury |
| `/compare` | Diff two runs, show deltas and regressions |
| `/knowledge` | CRUD for KB files + routing table |
| `/forge-kb` | Bootstrap knowledge base from document corpus |

### Starter KB Bootstrapping (future `/forge-agent` enhancement)

Beyond deterministic scaffolding, `/forge-agent` could bootstrap **starter KB files** by scouting references (LLM as scout → scraping → synthesis). Experiments runnable out of the box — users curate from a starting point, not a blank page.

### The Three-Command Demo

1. `/forge-agent --brief coverage.yaml` — scaffold project + starter KB
2. `/run` — execute and measure
3. `/grow` — iterate until the agent meets your standards

## Source Material

Converged from:
- `~/projects/agent-harness/` — MiniAgent, AgentLoopAdvisor, BashTool, observability (~13 classes copied into agent/)
- `~/projects/agent-harness-cli/` — TUI + MiniAgent integration pattern, three execution modes
- `~/projects/forge/` — ExperimentBrief, TemplateCloner, TemplateCustomizer
