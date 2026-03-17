# Roadmap: Loopy

> **Last updated**: 2026-03-13

## Overview

Active roadmap for **Wave 2** — Agent Quality + Modular Platform + Protocol Stack (MCP, ACP, A2A). Completed waves (Build + Wave 1, Stages 1-6) archived at `plans/archive/roadmap-waves-0-1.md`.

### Wave Summary

| Wave | Stages | Status | Focus |
|------|--------|--------|-------|
| **Build** | 1-4 | DONE | Foundation, TUI, MiniAgent, slash commands, `/forge-agent` (4.3 deferred) |
| **Wave 1** | 5-6 | DONE | Context compaction, Spring Boot, multi-provider, cost visibility |
| **Wave 2** | 7-11 | NEXT | Agent quality, modular platform (tool SPI, agent.yaml, profiles), protocol stack (MCP, ACP, A2A), knowledge activation |
| **Wave 3** | 12+ | future | Streaming, multi-model, observability dashboard, polish |

### Protocol Stack Roadmaps

Each major protocol integration has its own detailed roadmap to keep documents manageable:

| Roadmap | Stage | Status | Description |
|---------|-------|--------|-------------|
| [`roadmap-boot.md`](roadmap-boot.md) | 7.0 | **DONE** | `/boot-new`, `/starters`, `/boot-add`, `/boot-modify` — all 5 stages complete (2026-03-10) |
| [`roadmap-skills.md`](roadmap-skills.md) | 7.5 | **DONE (Stages 1–3)** | Stages 1–3 DONE (SkillsTool, `/skills`, 23-skill catalog). Stages 4–6 DEFERRED — SkillsJars handles packaging. |
| — | 8 | **DONE** | **Modular Foundation** — 8a DONE (AgentYaml, LoopyToolsFactory, profile bundles), 8b DONE (ToolProfileContributor SPI), 8c DONE (SkillsJars classpath scanning) |
| [`roadmap-mcp.md`](roadmap-mcp.md) | 9 | TODO | MCP client — `.mcp.json` standard format (DD-14), lazy startup |
| [`roadmap-acp.md`](roadmap-acp.md) | 9.5 | TODO | ACP agent mode (DD-19) — editors drive Loopy via stdio/WebSocket, ~3-6h |
| [`roadmap-a2a.md`](roadmap-a2a.md) | 10 | TODO | A2A client auto-config (DD-15) — declarative YAML, contribute upstream to spring-ai-a2a |

**Order rationale**: Modular Foundation first — tool plugin SPI (DD-12), `agent.yaml` (DD-13), and profile bundles (DD-16) are prerequisites for the declarative config story in MCP, ACP, and A2A. Without them, each protocol requires bespoke wiring. Skills Stages 4–6 deferred: SkillsJars project already handles skill packaging and classpath delivery; no Loopy-specific first-party starters needed now. MCP next: universal extensibility standard, zero user coding, `.mcp.json` already compatible with Claude Code/Cursor ecosystem. ACP alongside or after MCP: lightweight (~25 lines), stdio/subprocess maps to Loopy's CLI model, enables IDE editor integration (Zed, JetBrains, VS Code). A2A after ACP: multi-agent orchestration; Loopy contributes the missing client auto-config upstream.

**Research**: `plans/inbox/extensibility-plugin-vs-mcp.md` — full protocol stack analysis, CLI comparison, design sources.

See `plans/gap-inventory.md` for the full competitive gap analysis (20 gaps vs Goose v1.27.0).

> **Before every commit**: Verify ALL exit criteria for the current step are met. Do NOT remove exit criteria to mark a step complete — fulfill them.

---

## Stage 6.K: Documentation & Observability Cleanup (pre-Stage 7 gate)

> **Rationale**: Documentation drifted during rapid Wave 1 development, and there's no structured way to observe agent behavior. Fix both before Stage 7 — docs match the codebase, and the agent journal provides the foundation for debugging, user-facing verbosity, and the future TamboUI dashboard.

### Step 6.K.1: Doc Accuracy Pass

**Entry criteria**:
- [ ] Stage 6 complete

**Context**: Audit found 6 discrepancies between docs (README.md, docs/*.md) and actual code. Most are minor but the model default inconsistency and non-functional `--debug` flag could confuse users.

**Work items**:
- [x] FIX model default references: CLI defaults are per-provider (from `application.yml`), not hardcoded. Programmatic API defaults to `claude-sonnet-4-6`. Docs must distinguish.
- [x] ADD `.commandTimeout(Duration)` to README builder options table (was missing)
- [x] CLARIFY `.timeout()` default: "10 min" is inherited from `AgentLoopAdvisor`, not set in `LoopyAgent`
- [x] FIX architecture.md data flow: "Anthropic API" → multi-provider (Anthropic, OpenAI, Gemini)
- [x] IMPLEMENT `--debug` flag: file-based logging to `~/.local/state/loopy/logs/loopy-debug.log` (XDG convention, same pattern as Goose)
- [x] FIX Mockito self-attach warning: add `-javaagent` to surefire config
- [x] FIX JaCoCo missing version warning: pin to 0.8.14
- [x] VERIFY: `./mvnw test` — 61 tests pass, zero warnings

**Exit criteria**:
- [x] All docs match actual code behavior
- [x] `--debug` flag functional (file-based, interim — refactored in 6.K.3)
- [x] Tests pass: `./mvnw test`
- [x] COMMIT

---

### Step 6.K.2: Agent Journal (structured event log)

**Entry criteria**:
- [x] Step 6.K.1 complete

**Context**: Loopy has no structured way to observe agent behavior. Logs are ad-hoc via SLF4J. An append-only event journal provides a single data source for: (1) `--debug` user verbosity, (2) `LOOPY_DEBUG_LOG` dev file logging, (3) future TamboUI dashboard, (4) future OTLP/Langfuse export (GAP-18). Zero coupling to TUI — the journal is a library concern, not a presentation concern.

**Research**: TUI framework decision + dashboard concept at `~/tuvium/projects/tuvium-research-conversation-agent/analysis/tui-framework-decision.md`

**Implementation**: Used existing `journal-core` library (`io.github.markpollack:journal-core:1.0.1-SNAPSHOT`) instead of inline classes. Bridge listeners translate MiniAgent events to journal-core events.

**Work items**:
- [x] ADD `journal-core` Maven dependency (existing library with 279 tests, JSON Lines storage, Run lifecycle)
- [x] CREATE `JournalToolCallListener` — bridges `ToolCallListener` → `ToolCallEvent` (success/failure)
- [x] CREATE `JournalLoopListener` — bridges `AgentLoopListener` → `LLMCallEvent` on completion + `Run.fail()` on errors
- [x] CREATE `CompositeToolCallListener` — fans out to multiple `ToolCallListener`s
- [x] ADD `journalRun(Run)` to `MiniAgent.Builder` — wires both journal listeners automatically
- [x] WIRE journal `Run` lifecycle in all three modes (TUI, REPL, print) with proper close/fail handling
- [x] Journal output: `~/.local/state/loopy/journal/experiments/loopy-session/runs/{run-id}/` with `run.json`, `events.jsonl`
- [x] VERIFY: `./mvnw test` — 61 tests pass

**Exit criteria**:
- [x] Agent journal captures all loop and tool call events
- [x] Journal files readable with `cat` / `jq`
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 6.K.3: Debug Refactoring (two-concern split)

**Entry criteria**:
- [x] Step 6.K.2 complete (agent journal available)

**Context**: Current `--debug` writes logback DEBUG to a file. Based on CLI comparison (Goose separates `--debug` user verbosity from `RUST_LOG` dev logging), split into two concerns. The agent journal from Step 6.K.2 provides the structured event data that `--debug` renders.

**Work items**:
- [x] REFACTOR `--debug` flag = **user-facing verbosity**:
  - `DebugToolCallListener`: tool name, args summary, duration, result (truncated) → stderr
  - `DebugLoopListener`: turn numbers, completion stats (turns, tokens, cost) → stderr
  - Wired via `MiniAgent.Builder.loopListener()` (new method) + existing `toolCallListener()`
- [x] ADD `LOOPY_DEBUG_LOG=<path>` env var = **developer file logging**:
  - Logback file appender triggered by env var (was `--debug`, now `LOOPY_DEBUG_LOG`)
  - `LOOPY_DEBUG_LOG=1` or `true` → default `~/.local/state/loopy/logs/loopy-debug.log`
  - `LOOPY_DEBUG_LOG=/path/to/file` → custom path
- [x] UPDATE docs (README, cli-reference, configuration) — all three updated
- [x] VERIFY: `./mvnw test` — 61 tests pass

**Exit criteria**:
- [x] `--debug` shows user-facing agent activity on stderr
- [x] `LOOPY_DEBUG_LOG` writes dev-level logback output to file
- [x] Docs updated
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

## Stage 7: Agent Quality

> **Rationale**: Split into two sub-stages with different approaches:
>
> **Stage 7a — Standalone code improvements** (deterministic, no grow cycle needed): cost tracking, graceful tool errors, debug/journal observability, session persistence. These are correct-or-not improvements — implement directly.
>
> **Stage 7b — MiniAgent grow cycle** (needs terminal-bench → experiment-driver loop): mode-aware prompts, system prompt rewrite. These directly affect benchmark scores. Follow forge-methodology: genesis prompt → measure via terminal-bench (entry criterion: current baseline 32/35) → experiment-driver variants → graduate improved prompt → backport to agent-harness.
>
> **Research sources**:
> - System prompt comparison: `plans/research/system-prompt-improvements.md`
> - Agent loop comparison: `plans/research/agent-loop-comparison-findings.md`
> - Accurate cost tracking: `plans/research/accurate-cost-tracking.md`
> - Graceful tool errors: `plans/research/graceful-tool-errors.md`

### Step 7.0: System Prompt Rewrite — Stage 7b (grow cycle: genesis prompt)

**Entry criteria**:
- [x] Stage 6 complete
- [x] Read: `plans/research/system-prompt-improvements.md` — 9 gaps, infrastructure validation, deferred items

**Context**: Current system prompt is ~89 lines in `MiniAgentConfig.DEFAULT_SYSTEM_PROMPT`. Covers tool listing and selection rules. Missing convention enforcement, verification workflow, output style, safety, git discipline — patterns that every production CLI emphasizes. This is a pure text change with immediate quality impact.

**Known prompt bugs to fix** (from infrastructure validation):
- Remove nonexistent `LS` tool (→ replaced by ListDirectory in Step 7.0A)
- Fix `web_search` → `WebSearch`, `smart_web_fetch` → `WebFetch` (match actual `@Tool(name=...)`)
- Add `AskUserQuestionTool` (conditional — TUI mode only, deferred to Step 7.2 wiring)
- Remove "Execute one operation at a time" (contradicts batched tool call guidance)
- Match tool names to actual registrations (e.g., BashTool registers as `bash`)

**Work items**:
- [x] REWRITE `DEFAULT_SYSTEM_PROMPT` in `MiniAgentConfig.java`:
  - Convention enforcement: verify libraries exist, mimic project patterns, sparse comments
  - Output style: brief preamble before tool calls, file:line references, markdown only, concise responses
  - Safety: explain before destructive commands, never commit secrets, approval escalation
  - Git discipline: git status/diff/log before committing, never skip hooks, never force push
  - Batched tool calls: encourage multiple independent calls in one response (reduces LLM round-trips)
  - Search-first workflow: Grep/Glob for structure, then Read for context
  - Fix all tool name mismatches (see bugs above)
- [x] DO NOT reference deferred capabilities (see `plans/research/system-prompt-improvements.md` § Deferred Items)
- [x] VERIFY: `./mvnw test` (3 pre-existing failures unrelated to prompt change)

**Exit criteria**:
- [x] System prompt covers all 6 prompt-only areas above
- [x] All tool names in prompt match actual `@Tool(name=...)` registrations
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.0A: ListDirectory Tool (small infrastructure)

**Entry criteria**:
- [ ] Step 7.0 complete (or can be done in parallel)

**Context**: Both Codex CLI (`list_dir` with pagination/depth) and Gemini CLI (`list_directory`) provide dedicated directory listing tools. Loopy's prompt listed `LS` but no such tool existed — models calling "LS" fell through to the bash fallback via case-insensitive resolver. A dedicated tool gives smaller/cleaner output than `bash ls`, is easier for models to parse, and avoids wasting a bash invocation for simple directory listing.

**Work items**:
- [x] IMPLEMENT `ListDirectoryTool` in `spring-ai-agent-utils` (preferred — benefits all consumers)
  - Parameters: `path` (required), `depth` (default 1), `limit` (default 50)
  - Output: sorted file/directory listing with type indicators (file/dir)
  - Respect `.gitignore` patterns (skip `node_modules/`, `.git/`, `target/`, etc.)
- [x] REGISTER in `MiniAgent` tool list
- [x] UPDATE system prompt tool listing to include `ListDirectory`
- [x] WRITE unit tests
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] `ListDirectory` tool registered and functional
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.0B: Subagent Infrastructure Completion

**Entry criteria**:
- [ ] Step 7.0A complete (or can be done in parallel)

**Context**: `TaskTool` is wired in MiniAgent but four gaps leave the feature partially broken. Background tasks can be launched but results can never be retrieved (no `TaskOutputTool`). Users can't define custom subagents in `.claude/agents/` (directory never scanned). Multi-model routing is silently broken — subagent definitions with `model: haiku` fail at runtime because only `"default"` is registered. Skills are not propagated to subagents even though the paths are already resolved in the builder.

All four fixes are pure wiring changes to `MiniAgent.java`. No new abstractions needed.

**Work items**:
- [x] FIX `TaskOutputTool` not registered: share the `DefaultTaskRepository` instance between `TaskTool` and a new `TaskOutputTool` registration. Background task results become retrievable.
- [x] FIX custom agent loading: scan `.claude/agents/` in the working directory and `~/.claude/agents/` globally using `ClaudeSubagentReferences.fromRootDirectory()`. Add references to the `TaskTool.builder()` call. Skip gracefully if the directory doesn't exist.
- [x] FIX multi-model routing: register named `ChatClient.Builder` entries (`"haiku"`, `"sonnet"`, `"opus"`) in `ClaudeSubagentType.builder()` using per-request model override via `ChatOptions`. The same underlying `ChatModel` is used — no new API credentials needed.
- [x] FIX skills propagation to subagents: pass the same skills directories resolved for the main agent into `ClaudeSubagentType.builder().skillsDirectories()`.
- [x] UPDATE `docs/subagents.md` — custom agent loading section now accurate (`.claude/agents/` actually scanned), `TaskOutput` tool documented, multi-model routing note updated.
- [x] WRITE unit tests: TaskOutputTool wired with same repo; custom agents dir scan (with and without directory present).
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] Background tasks retrievable via `TaskOutput` tool
- [x] `.claude/agents/*.md` files in working directory registered as subagents
- [x] `~/.claude/agents/*.md` global agents scanned
- [x] Multi-model routing works for `haiku`, `sonnet`, `opus` aliases
- [x] Skills directories propagated to subagents
- [x] Tests pass: `./mvnw test`
- [ ] Create: `plans/learnings/step-7.0B-subagent-infra.md`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.1: AGENTS.md Support (small, prompt-only)

**Entry criteria**:
- [ ] Step 7.0 complete

**Context**: AGENTS.md is the cross-vendor standard for project-level agent instructions (Linux Foundation / Agentic AI Foundation). Adopted by Codex, Gemini CLI, Jules, Cursor, Aider, Zed, 60,000+ repos. Loopy already reads `CLAUDE.md` — should also read `AGENTS.md` from the working directory. Hierarchical: leaf files in subdirectories override root. Spec: https://agents.md/

**Work items**:
- [x] READ `AGENTS.md` from working directory (same auto-injection as `CLAUDE.md`)
- [x] If both exist, append both to system prompt (AGENTS.md first, CLAUDE.md second — CLAUDE.md is more specific)
- [x] WRITE test verifying AGENTS.md content appears in system prompt
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] AGENTS.md read and injected into system prompt
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.2: Mode-Aware Prompt Sections — Stage 7b (grow cycle: mode-aware prompts)

**Entry criteria**:
- [ ] Step 7.1 complete
- [ ] terminal-bench baseline score confirmed (current: 32/35 — entry criterion for 7b grow cycle)

**Context**: Loopy has three execution modes (TUI, print, REPL) but the system prompt is identical across all three. Production CLIs tailor instructions by mode — batch mode agents verify proactively, interactive agents ask clarifying questions. This step directly affects benchmark scores (terminal-bench runs in print mode) and is an experiment target: genesis prompt first, then measure, then experiment-driver variants, then graduate.

**Work items**:
- [ ] ADD mode parameter to `MiniAgentConfig` (enum: TUI, PRINT, REPL)
- [ ] APPEND mode-specific sections to system prompt:
  - TUI: conversational, can ask clarifying questions via AskUserQuestionTool, lazy verification
  - Print: batch mode, handle ambiguity independently, proactive verification (run tests/build after changes)
  - REPL: hybrid — brief responses, no user questions
- [ ] WIRE mode from `LoopyCommand` execution path into `MiniAgentConfig`
- [ ] RUN terminal-bench after genesis prompt: `~/projects/agent-harness-cli/tests/ai-driver/`
- [ ] RECORD baseline delta vs pre-7b score — this is the grow cycle entry measurement
- [ ] VERIFY: `./mvnw test`

**Exit criteria**:
- [ ] Mode-specific prompt sections active
- [ ] terminal-bench run recorded (score ≥ 32/35 to confirm no regression; target TBD)
- [ ] Tests pass: `./mvnw test`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT
- [ ] **Grow cycle gate**: if score plateaus, continue with experiment-driver variants → graduate improved prompt → backport to agent-harness

---

### Step 7.3: Accurate Cost Tracking (infrastructure)

**Entry criteria**:
- [ ] Step 7.2 complete
- [ ] Read: `plans/research/accurate-cost-tracking.md`

**Context**: Cost estimation uses a hardcoded Sonnet rate regardless of model. Haiku sessions report ~$5 when actual cost is ~$0.10, triggering premature `COST_LIMIT_EXCEEDED`. Need model-aware pricing with separate input/output token tracking.

**Work items**:
- [ ] ADD `inputTokensUsed` and `outputTokensUsed` fields to `LoopState`
- [ ] UPDATE `AgentLoopAdvisor.extractTokensUsed()` to track input/output separately from `Usage.getPromptTokens()` / `Usage.getGenerationTokens()`
- [ ] IMPLEMENT model-aware `estimateCost()` with per-model rates (Haiku $0.80/$4, Sonnet $3/$15, Opus $15/$75, GPT-4o $2.50/$10, etc.)
- [ ] SURFACE `inputTokens` and `outputTokens` in `LoopyResult` alongside existing `totalTokens`
- [ ] WRITE unit tests for cost calculation accuracy
- [ ] VERIFY: `./mvnw test`

**Exit criteria**:
- [ ] Cost estimates accurate per model
- [ ] Input/output tokens tracked separately
- [ ] Tests pass: `./mvnw test`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

### Step 7.4: Graceful Tool Errors (infrastructure — PARTIALLY DONE)

**Entry criteria**:
- [ ] Step 7.3 complete
- [ ] Read: `plans/research/graceful-tool-errors.md`

**Context**: When a model sends malformed tool arguments (common with non-Anthropic models), Spring AI's `DefaultToolExecutionExceptionProcessor` rethrows `IOException` subclasses, crashing the agent loop. Should return errors to the model instead, letting the loop continue.

**Already implemented** (from Qwen3-Coder work in code-coverage-experiment):
- ✅ Custom `toolExecutionExceptionProcessor` in `MiniAgent.java:171-178` — returns error messages to model
- ✅ Case-insensitive `toolCallbackResolver` in `MiniAgent.java:153-169` — handles "Bash" vs "bash" capitalization
- ✅ Bash fallback for unknown tool names — loop continues instead of crashing

**Remaining work items**:
- [x] VERIFY edge cases: do `IOException` subclasses (e.g., from file tools) still crash? Test with intentional bad paths
- [x] WRITE unit tests: malformed args → error returned to model, loop continues
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] Tool deserialization errors returned to model, not thrown (verified, not just assumed)
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.5: Graceful Termination Recovery (infrastructure + prompt)

**Entry criteria**:
- [ ] Step 7.4 complete
- [ ] Read: `plans/research/agent-loop-comparison-findings.md` — Gap 2

**Context**: When MiniAgent hits max turns or timeout, it throws immediately. Gemini CLI's pattern: give the agent a 60-second grace period with tools restricted to output-only, recovering a useful partial result ~50% of the time.

**Work items**:
- [x] IMPLEMENT grace period in `AgentLoopAdvisor`: before throwing termination exception, inject one more turn with constrained prompt: "You have reached the turn/time limit. Summarize your progress and provide your best output now."
- [x] RESTRICT available tools in grace turn to output-only (Submit, TodoWrite)
- [x] ADD `COMPLETED_WITH_WARNING` status to `MiniAgentResult` (distinct from `TURN_LIMIT_REACHED`)
- [x] WRITE unit tests: max turns reached → grace turn fires → partial result returned
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] Grace period fires before termination
- [x] `COMPLETED_WITH_WARNING` returned when grace turn succeeds
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.6: Stuck Detection — Tool Call Hashing (infrastructure)

**Entry criteria**:
- [ ] Step 7.5 complete
- [ ] Read: `plans/research/agent-loop-comparison-findings.md` — Gap 3

**Context**: MiniAgent checks consecutive identical output hashes but misses alternating patterns (A-B-A-B) and tool call loops with slightly different args. Gemini's Tier 1 (tool call hashing) is a quick win: SHA256 of `{toolName}:{argsJSON}`, threshold of 5 consecutive identical calls.

**Work items**:
- [x] IMPLEMENT tool call hash tracking in `AgentLoopAdvisor`:
  - Hash each tool call: `SHA256(toolName + ":" + argsJSON)`
  - Track consecutive identical hashes (threshold: 5)
  - Also track alternating patterns (A-B-A-B, window of 10)
- [x] TRIGGER graceful termination (Step 7.5) when stuck detected
- [x] ADD `STUCK_DETECTED` termination reason
- [x] WRITE unit tests: repetition → stuck, alternation → stuck, varied calls → no trigger
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] Tool call repetition and alternation detected
- [x] Stuck detection triggers graceful termination
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.7: Stream Error Retry (infrastructure, LOW priority)

**Entry criteria**:
- [ ] Step 7.6 complete
- [ ] Read: `plans/research/agent-loop-comparison-findings.md` — Gap 4

**Context**: MiniAgent uses synchronous `ChatClient.call()`. API failures propagate as exceptions. Spring AI's HTTP client may already have retry policies — check before implementing. Low priority for v1; becomes important when moving to streaming.

**Work items**:
- [ ] INVESTIGATE Spring AI's built-in retry behavior
- [ ] IF needed: add retry with exponential backoff (2-3 attempts, 500ms initial delay) around `ChatClient.call()`
- [ ] VERIFY: `./mvnw test`

**Exit criteria**:
- [ ] Transient API failures retried (or documented as handled by Spring AI)
- [ ] Tests pass: `./mvnw test`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

---

### Step 7.8: Session Persistence (infrastructure)

**Entry criteria**:
- [ ] Step 7.7 complete

**Context**: Closing Loopy loses all context — can't resume a conversation. Goose has SQLite-backed session persistence with resume, search, and export. For Loopy, a simpler approach: serialize message history to JSON, add `/session` slash commands. Cross-session learning and experiment resume depend on this. GAP-6 in gap inventory.

**Work items**:
- [x] DESIGN session storage format: JSON file per session in `~/.config/loopy/sessions/`
  - Filename: `{timestamp}-{first-4-words}.json`
  - Content: message history + metadata (model, provider, working directory, cost)
- [x] IMPLEMENT `SessionStore`: `save(messages, metadata)`, `load(sessionId)`, `list()`, `search(query)`
- [x] ADD `/session` slash commands:
  - `/session save [name]` — save current session (auto-named if no name)
  - `/session list` — list saved sessions
  - `/session load <id>` — restore a previous session
- [x] AUTO-SAVE on `/quit` and on `COMPLETED` agent result (configurable)
- [x] WIRE into `ChatScreen` and REPL mode
- [x] WRITE unit tests for SessionStore serialization round-trip
- [x] VERIFY: `./mvnw test`

**Exit criteria**:
- [x] Sessions persist across Loopy restarts
- [x] `/session` commands work in TUI and REPL
- [x] Tests pass: `./mvnw test`
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

### Step 7.K: Stage 7 Consolidation

**Entry criteria**:
- [ ] All Stage 7 steps complete (7.0–7.8)

**Work items**:
- [x] UPDATE `CLAUDE.md` with distilled learnings
- [x] VERIFY all tests pass: `./mvnw verify`

**Exit criteria**:
- [ ] System prompt comprehensive and mode-aware (7.2 pending)
- [x] Agent loop hardened: graceful errors, graceful termination, stuck detection
- [x] Sessions persist across restarts
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT

---

## Stage 7.5: Agent Starters — Skills as Spring Boot Starters for AI Agents

> See [`plans/roadmap-skills.md`](roadmap-skills.md) for detailed steps.
>
> **Core insight**: Add dependency → agent becomes smarter. Same mental model as Spring Boot starters. Same classpath discovery (`META-INF/agent-skills/`). Same distribution (Maven Central). Two paths: `<dependency>` for Spring AI apps, `/skills add` for Loopy CLI. Loopy is the reference consumer; any Spring AI app can use Agent Starters.
>
> **Status**: Stages 1–3 DONE (SkillsTool wired, `/skills` command, 23-skill curated catalog with search/add/remove). **Stages 4–6 DEFERRED** — the SkillsJars project (`com.skillsjars`) already handles first-party skill packaging and classpath delivery. No Loopy-specific first-party starters needed now. Revisit once the modular platform (Stage 8) is complete.

---

## Stage 8: Modular Foundation

> **Prerequisite for all protocol integrations.** Implements DD-12, DD-13, DD-16, DD-17: tool plugin SPI, declarative `agent.yaml`, profile bundles, headless runtime.

### Step 8.a: SPI-first validation — AgentYaml + LoopyToolsFactory ✅ DONE

- [x] `AgentYaml` + `AgentYamlLoader` — post-boot loading; `./agent.yaml` → `~/.config/loopy/agent.yaml` → defaults
- [x] `ToolFactoryContext` record — workingDirectory, chatModel, commandTimeout, interactive, agentCallback
- [x] `LoopyToolsFactory` — profile bundles: `dev`, `boot`, `headless`, `readonly`; unknown profiles logged and skipped
- [x] `MiniAgent.Builder.profileToolCallbacks()` — new path; legacy path preserved for backwards compat
- [x] `LoopyCommand.createAgent()` — loads `agent.yaml`, calls factory, passes profile tools to builder
- [x] Boot tools (`BootNewTool`, `BootModifyTool`) moved out of `LoopyCommand` into `boot` profile in factory
- [x] 16 new tests: `AgentYamlLoaderTest` (8), `LoopyToolsFactoryTest` (8)
- [x] COMMIT

### Step 8.b: ToolProfileContributor SPI ✅ DONE

Custom tool JARs drop onto the classpath as Maven dependencies (declared in the agent project's `pom.xml`). The classpath IS the plugin directory — no global tool directories, no cross-agent conflicts.

- [x] `ToolProfileContributor` interface — `profileName()` + `tools(ToolFactoryContext)`, registered via `META-INF/services`
- [x] `LoopyToolsFactory` — `ServiceLoader` lookup after built-in switch; injectable overload for testing
- [x] Warning updated: unknown profiles log the contributor registration path
- [x] 2 new tests: contributor resolved, contributor cannot override built-in profile
- [x] COMMIT

### Step 8.c: SkillsJars classpath scanning (forged agent projects) ✅ DONE

Applies to **forged agent projects** (standalone Maven projects, no TUI, no `/skills` command). Loopy CLI's existing `/skills add` + disk catalog already handles the interactive case.

A forged agent project wires `SkillsTool` into its agent loop. Declaring a SkillsJar in the project's `pom.xml` is sufficient — `SkillsTool` scans `META-INF/skills/**` and `META-INF/resources/skills/**` on the classpath at startup. Skills are available to the agent loop automatically. No install step, no extraction for markdown, no catalog update. Same distribution model as any other Maven dependency.

**Implementation** (all in `MiniAgent.java`):
- `ClassPathResource("META-INF/skills")` and `ClassPathResource("META-INF/resources/skills")` passed to `SkillsTool.Builder.addSkillsResource()` at construction time
- `Skills.loadResource()` (in spring-ai-agent-utils) handles the classpath → JAR scan path; `PathMatchingResourcePatternResolver` and manual JAR scan fallback already implemented there
- `/skills list` in `SkillsCommand` also shows classpath skills (same scanning path)
- Script extraction deferred: classpath skills are `.md` only in the SkillsJars convention; scripts would require a separate extraction step (future if needed)

**Work items**:
- [x] `MiniAgent` scans `META-INF/skills/**` + `META-INF/resources/skills/**` from classpath at startup
- [x] `SkillsCommand` (`/skills list`) shows classpath-discovered skills alongside filesystem skills
- [x] Test classpath resource: `src/test/resources/META-INF/skills/test-org/test-repo/classpath-skill/SKILL.md`
- [x] `ClasspathSkillsScanningTest` — 4 tests: classpath-only, merged, coexistence, explicit disable
- [x] `SkillsIntegrationTest` — 2 tests updated to reflect classpath skills always present in test JVM
- [x] `SkillsCommandTest` — `listNoSkills` updated: verifies classpath skill appears in `/skills list`
- [x] All 303 tests pass: `./mvnw test`
- [x] COMMIT

---

## Stage 9: MCP Client

> See [`plans/roadmap-mcp.md`](roadmap-mcp.md) for detailed steps.
>
> `.mcp.json` standard format (DD-14), lazy MCP server startup, tools registered as `ToolCallback` peers. Compatible with Claude Code / Cursor `.mcp.json` files out of the box.

---

## Stage 9.5: ACP Agent Mode

> See [`plans/roadmap-acp.md`](roadmap-acp.md) for detailed steps.
>
> Loopy as ACP agent (DD-19) — editors (Zed, JetBrains, VS Code) drive Loopy via stdio or WebSocket. Annotation-based (`@AcpAgent`, `@Prompt`), ~3-6 hours estimated. `--acp` flag activates ACP listener instead of TUI.

---

## Stage 11: Knowledge Activation + KB Bootstrapping (future)

> **Rationale**: Two complementary features: (1) passive knowledge activation triggered by file access patterns — the harness monitors which files the agent reads and automatically injects matching knowledge, zero agent turns consumed; (2) starter KB bootstrapping via reference harvest in `/forge-agent` (deferred from Stage 4.3).
>
> **Dependencies**: Stage 8 complete (MCP + Skills). Research: Stripe deep dive at `~/tuvium/projects/tuvium-research-conversation-agent/journal/2026-03-03.md`, starter KB bootstrapping at `plans/archive/starter-kb-bootstrapping.md`, reference harvest guide at `/home/mark/tuvium/projects/forge-methodology/guides/reference-harvest.md`.
>
> **Scope** (detailed steps to be written when this stage begins):
> - `KnowledgeActivationAdvisor` (Spring AI `CallAdvisor`) — glob pattern → knowledge injection on file access
> - `KnowledgeRule`, `FileAccessTracker` — YAML rule format, tool call result parsing
> - GAP-16 partial: compaction sophistication (dual visibility, progressive tool filtering)
> - Starter KB bootstrapping in `/forge-agent` (Firecrawl + LLM reference harvest, formerly Step 4.3)
> - `/run`, `/grow`, `/judge` commands (experiment-driver integration)
> - Integration tests with Spring project scenario
> - **Hierarchical CLAUDE.md/AGENTS.md traversal**: walk from filesystem root down to CWD, loading `CLAUDE.md` and `.claude/CLAUDE.md` at every level (root-first, innermost last). This is what Claude Code does (confirmed via reverse engineering at `~/tuvium/claude-code-analysis/cli.readable.js:353766`). Step 7.1 does root-only (same as Goose/Gemini) — full traversal belongs here alongside `KnowledgeActivationAdvisor`.

---

## Stage 10: A2A Multi-Agent Orchestration

> See [`plans/roadmap-a2a.md`](roadmap-a2a.md) for detailed steps.
>
> Declarative A2A client (DD-15) — `agent.yaml` `a2a.client.agents` block, `A2AClientAutoConfiguration`, remote agents as `ToolCallback` peers. Contribute `spring-ai-a2a-client-autoconfigure` upstream to `spring-ai-community/spring-ai-a2a`. Optional A2A server mode.

---

## Future Waves

### Observability & Dashboard

> **Prerequisite**: Agent journal (Step 6.K.2) is the foundation — `--debug`, dev file logging, and the TamboUI dashboard are *readers* of the same event stream.

| Feature | Description | Effort |
|---------|-------------|--------|
| Agent journal | **Done** (Step 6.K.2) | — |
| `--debug` verbosity | **Done** (Step 6.K.3) | — |
| `LOOPY_DEBUG_LOG` | **Done** (Step 6.K.3) | — |
| `loopy dashboard` | TamboUI companion terminal — "htop for agent runs" | Large |
| GAP-18 | Observability export (Micrometer → OTLP, Langfuse) | Medium |

### Infrastructure & Polish

| Gap | Feature | Effort |
|-----|---------|--------|
| GAP-7 | Streaming responses (`ChatModel.stream()`) | Medium |
| GAP-8 | Package managers (brew, sdkman) | Medium |
| GAP-9 | `loopy configure` wizard | Medium |
| GAP-10 | Multi-model lead/worker | Medium |
| GAP-17 | Security/permissions (smart approve) | Large |
| GAP-19 | tree-sitter code parsing | Large |
| GAP-20 | Recipe/task definition system | Medium |
| Loop Gap 5 | Parallel tool execution | Medium |

### loopy-agent-spring-boot-starter (future product direction)

> **Rationale**: Embed an agent inside an existing Spring Boot app to make it "smart." `ToolProfileContributor` becomes a Spring `@Bean` (Spring context replaces `ServiceLoader`). `@Tool`-annotated service methods auto-discovered. Exposes `/agent` endpoint or WebSocket for chat. The agent runs in the same JVM as the app — accesses domain services directly, same transaction context.
>
> **PetClinic demo**: add the starter → annotate `OwnerService` methods with `@Tool` → natural language CRUD over the domain model. Zero boilerplate.
>
> **Relationship to Loopy CLI**: same core abstractions (`ToolProfileContributor`, `agent.yaml`, MiniAgent) — delivery mechanism adapts to the container (Spring beans vs. `ServiceLoader`). CLI is the developer workbench; starter is the runtime component for apps.
>
> **When**: after Stage 8 (modular foundation solid), MCP integration proven. The starter inherits MCP + A2A capabilities immediately.

### Spring CLI Revival

> **Complete.** `roadmap-boot.md` Stage 7.0 is fully done (2026-03-10). `/boot-new` (4 templates, ScaffoldGraph, JavaParserRefactor), `/starters` (catalog + suggest), `/boot-add` (SAE analysis + code gen), `/boot-modify` (11 deterministic recipes, LLM classifier, full agent fallback). No Initializr API, knowledge-directed, deterministic POM manipulation via MavenXpp3.

### Unscheduled Small Wins

- GAP-12: Troubleshooting docs (`docs/troubleshooting.md`)
- GAP-13: Contributing guide (`CONTRIBUTING.md`)

---

## Directory Structure

```
plans/
├── ROADMAP.md                         # This file — main roadmap + index
├── roadmap-boot.md                    # Stage 7.0: Agent Starters + boot scaffolding
├── roadmap-skills.md                  # Stage 7.5: Skills integration
├── roadmap-mcp.md                     # Stage 8: MCP client
├── roadmap-acp.md                     # Stage 8.5: ACP agent mode
├── roadmap-a2a.md                     # Stage 10: A2A multi-agent
├── DESIGN.md                          # Architecture decisions
├── VISION.md                          # Product vision
├── gap-inventory.md                   # Competitive gap analysis (20 gaps vs Goose)
├── inbox/                             # Unprocessed ideas, research briefs
│   ├── extensibility-plugin-vs-mcp.md # Protocol stack research (source for feature roadmaps)
│   └── journal-core-distribution.md   # journal-core packaging strategy
├── learnings/                         # Per-step learnings
│   ├── LEARNINGS.md                   # Compacted summary
│   └── step-*.md                      # Individual step learnings
├── research/                          # Active research informing upcoming stages
│   ├── agent-loop-comparison-findings.md
│   ├── system-prompt-improvements.md
│   ├── accurate-cost-tracking.md
│   ├── graceful-tool-errors.md
│   └── custom-chatmodel-support.md
└── archive/                           # Completed/superseded
    ├── roadmap-waves-0-1.md           # Full Stages 1-6 with checkboxes
    └── ...
```

---

## Conventions

### Step Exit Criteria Convention

Every step's exit criteria must include:
```markdown
- [ ] Tests pass: `./mvnw test`
- [ ] Create: `plans/learnings/step-X.Y-topic.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT
```

---

## Revision History

> Full history for Waves Build + 1 in `plans/archive/roadmap-waves-0-1.md`.

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-06 | Add Stage 7 (Agent Quality) + Stage 8 (Knowledge Activation). Wave summary. Retire inbox. | Wave 1 complete |
| 2026-03-07 | Split roadmap: archive Stages 1-6 to `plans/archive/roadmap-waves-0-1.md`. Move Step 4.3 (KB bootstrapping) to Stage 8. Add AGENTS.md support (Step 7.1), session persistence (Step 7.8). Add future waves (3, 4, 2B) with gap mapping. | Roadmap review and cleanup |
| 2026-03-08 | Add protocol stack roadmaps: Skills (7.5), MCP (8), ACP (8.5), A2A (10). Each in separate file. Renumber Knowledge Activation to Stage 9. Update wave summary. | Extensibility research + /plan-to-roadmap |
| 2026-03-08 | Elevate Skills to "Agent Starters" vision. Full Forge methodology roadmap with 5 stages. | Agent Starters strategic insight |
| 2026-03-09 | Add roadmap-boot.md (Stage 7.0) as Wave 2 priority #1. Supersede Spring CLI Revival section. | valiant-gliding-hellman plan approved |
| 2026-03-10 | Mark roadmap-boot.md DONE (all 5 stages complete). Update skills status (Stages 1–3 done, Stage 4 next). Update Spring CLI Revival note. | Boot scaffolding complete |
| 2026-03-12 | Add Step 7.0B: Subagent Infrastructure Completion — four gaps vs blog post: TaskOutputTool unregistered, custom agents not scanned, multi-model routing broken, skills not propagated. | Blog post gap analysis |
| 2026-03-12 | Mark Step 7.0 DONE: system prompt rewritten with convention enforcement, git discipline, batched tool calls, output style, safety/security. All 6 prompt-only areas from research doc addressed. | Step 7.0 complete |
| 2026-03-12 | Mark Steps 7.0A, 7.0B, 7.1, 7.4, 7.5, 7.6, 7.8 DONE. ListDirectory, subagent infra (TaskOutputTool, custom agents, multi-model routing, skills propagation), AGENTS.md injection, tool error tests, grace turn, stuck detection, session persistence. | Roadmap sync |
| 2026-03-13 | Add Stage 8 (Modular Foundation — DD-12/13/16/17). Renumber MCP→9, ACP→9.5, Knowledge Activation→11. Move A2A from Wave 3 to Wave 2. Mark Skills Stages 4–6 DEFERRED (SkillsJars handles packaging). Update order rationale. Wave summary updated. | Design review: modular platform |
| 2026-03-13 | Stage 8a DONE (AgentYaml, AgentYamlLoader, LoopyToolsFactory, ToolFactoryContext, MiniAgent.profileToolCallbacks). Stage 8b DONE (ToolProfileContributor SPI via ServiceLoader, injectable for testing). Add Stage 8c (SkillsJars classpath scanning — META-INF/skills/**, script extraction to project-local ./skills/). Add loopy-agent-spring-boot-starter to Future Waves. Classpath IS the plugin directory — no ~/.config/loopy/tools/ global dir. | Stage 8 implementation + AgentJars design discussion |
| 2026-03-17 | Stage 8c DONE (SkillsJars classpath scanning). MiniAgent scans META-INF/skills + META-INF/resources/skills at startup; SkillsCommand /skills list also shows classpath skills. ClasspathSkillsScanningTest (4 tests) + SkillsIntegrationTest/SkillsCommandTest updated. Stage 8 DONE. Next: Stage 9 (MCP). | Stage 8c implementation |
