# Spring Boot Scaffolding

Loopy includes a full Spring Boot scaffolding layer — a knowledge-directed replacement for Spring CLI. Four slash commands cover the complete project lifecycle: create, discover capabilities, add capabilities, and modify structure.

- [/boot-new](#boot-new) — scaffold a new Spring Boot project from a bundled template
- [/starters](#starters) — discover Agent Starters and get suggestions for your project
- [/boot-add](#boot-add) — bootstrap domain capabilities into an existing project
- [/boot-modify](#boot-modify) — apply structural modifications (set Java version, add native support, add CI, etc.)

All commands work on Maven projects (`pom.xml` required, except `/boot-new`). No calls to `start.spring.io` or any external service — all knowledge is bundled.

---

## /boot-new

Scaffold a new Spring Boot project from a bundled stem-cell template. Accepts either structured flags or plain natural language.

```
/boot-new --template <name> --name <project-name> --group <group-id> [--no-llm]
/boot-new <natural language description>
```

### Natural language path

Plain text without `--` flags is routed through the agent, which calls `BootNewTool` with the right parameters extracted from your description. The LLM fills in template, group, name, and Java version — you describe the intent, the agent handles the details.

```
/boot-new a REST API called orders-api for com.acme
/boot-new create a JPA project named catalog-service for com.corp with Java 17
/boot-new scaffold a minimal Spring Boot app named hello-world for io.example
/boot-new I need a Spring AI application for com.myorg called assistant-bot
/boot-new build me a REST + JPA service named inventory-api, group com.warehouse
```

You can also skip the slash command entirely — typing in the chat window works too:

```
create a new Spring Boot project named products-api for com.acme using the REST template
scaffold a JPA app called user-service for io.example with Java 21
```

### Structured flags path

```
/boot-new --template spring-boot-rest --name products-api --group com.acme
/boot-new --template spring-boot-jpa --name inventory --group com.example --no-llm
/boot-new --template spring-ai-app --name my-agent --group io.myorg
/boot-new --name widget-service --group com.corp --java-version 17 --no-llm
```

### Templates

| Template | Description |
|----------|-------------|
| `spring-boot-minimal` | Bare Spring Boot application. No web, no persistence. Starting point for anything. |
| `spring-boot-rest` | REST API with `spring-boot-starter-web`, validation, `GreetingController`, MockMvc tests. |
| `spring-boot-jpa` | REST + JPA with H2 (test scope), `@Entity`, `JpaRepository`. |
| `spring-ai-app` | Spring AI application with `ChatClient` wiring and Anthropic auto-config. |

### What it does

1. Copies the template from the bundled JAR to a new directory in the working directory
2. Renames the Maven GAV (`groupId`, `artifactId`, `version`) in `pom.xml`
3. Renames Java packages using the AST (JavaParser) — correct package declarations and directory layout, no string replacement
4. Applies any saved preferences (default `groupId`, Java version) from `~/.config/loopy/boot/preferences.yml`
5. Optionally runs a bounded AI pass to tailor `README.md` and `application.yml` stubs to your domain (skip with `--no-llm`)

### Package naming

The Java package is derived from `--group` and `--name`:
- `--group com.acme --name products-api` → package `com.acme.productsapi`
- `--group io.example --name order-service` → package `io.example.orderservice`

### Saved preferences

After a successful `/boot-new`, Loopy saves `groupId` and `javaVersion` to `~/.config/loopy/boot/preferences.yml`. Subsequent runs pre-fill those values so you don't repeat yourself.

---

## /starters

Discover Agent Starters — Spring Boot starters that give your AI agent deep domain expertise.

```
/starters                      # List available starters
/starters list                 # Same
/starters search <query>       # Search by name, description, or trigger dependency
/starters info <name>          # Starter details + Maven coordinates
/starters suggest              # Suggest starters based on your project's pom.xml
```

### What is an Agent Starter?

An Agent Starter (`spring-ai-starter-{domain}`) is a Spring Boot starter that packages domain knowledge for AI agents — the full package: a skill file, project analyzer, auto-configuration, and tool implementations. Add it as a Maven dependency and any Spring AI application gains that domain expertise automatically.

Agent Starters are separate from the curated skills catalog (`/skills`). Skills are knowledge-only; Agent Starters are the full package.

### suggest

`/starters suggest` reads your `pom.xml` and cross-references its dependencies against the starter catalog's trigger list. If you have `spring-boot-starter-data-jpa` in your POM, it will suggest `spring-ai-starter-data-jpa`.

```
/starters suggest
```

---

## /boot-add

Bootstrap domain capabilities into an existing Spring Boot project.

```
/boot-add <starter-name> [--no-agent] [--coords groupId:artifactId[:version]]
```

`/boot-add` is primarily a **code generation command** that happens to add a dependency first. The main value is the AI pass that generates domain-specific code tailored to your actual project structure — not generic samples.

### What it does

1. **Analyze** — runs a static project analysis (`PROJECT-ANALYSIS.md` written to the project root). This gives the agent full context: your package names, existing classes, test patterns, import paths.
2. **Add dependency** — adds the starter's Maven coordinates to `pom.xml` using the Maven object model (no string editing)
3. **Generate code** — bounded AI agent reads `PROJECT-ANALYSIS.md` and the starter's skill to generate domain-specific code patterns that fit your actual project structure

Skip the AI pass with `--no-agent` to only run steps 1–2.

### Examples

```
/boot-add spring-ai-starter-data-jpa
/boot-add spring-ai-starter-data-jpa --no-agent
/boot-add my-lib --coords com.example:my-lib:1.0.0
```

### PROJECT-ANALYSIS.md

The analysis file written by `/boot-add` (and used by `/boot-modify`) is a Static Analysis Enhancement (SAE) document. It contains:

- Dependencies and versions from `pom.xml`
- Production class inventory with types, package names, and key annotations
- Existing test classes and test slice configuration
- Component classification (controllers, services, repositories, entities)
- Fully-qualified import blocks per test slice — so a subsequent AI agent can write correct tests without probing JARs

You can inspect it at the project root after running `/boot-add`.

---

## /boot-modify

Apply structural modifications to an existing Spring Boot project using natural language.

```
/boot-modify <intent>
```

One command handles all structural modification intents. Describe what you want in plain English.

### How it works

`/boot-modify` routes your intent through MiniAgent, which selects the right `@Tool` method from `BootModifyTool` based on your natural language description. Each tool method handles one operation and executes it deterministically via Maven's object model — the agent picks the tool, it never writes XML.

```
/boot-modify <anything> → MiniAgent → BootModifyTool.@Tool method → pom.xml / filesystem
```

This means any natural language variation of an intent maps to the same deterministic operation. You don't need to know the exact keyword.

### Available operations

Each row is a separate `@Tool` method on `BootModifyTool`. The agent selects the right one based on your intent.

| Operation | Example intents |
|-----------|----------------|
| Set Java version | `/boot-modify set java version 21` · `/boot-modify upgrade to Java 21` · `/boot-modify use Java 17` |
| Clean POM | `/boot-modify clean pom` · `/boot-modify remove empty fields from the pom` · `/boot-modify normalize the pom file` |
| Add GraalVM native image | `/boot-modify add native image support` · `/boot-modify enable GraalVM compilation` · `/boot-modify I want a native executable` |
| Remove GraalVM native image | `/boot-modify remove native image support` · `/boot-modify drop the native-maven-plugin` |
| Add Spring Java Format | `/boot-modify add spring format enforcement` · `/boot-modify enforce Spring code style` · `/boot-modify add the javaformat plugin` |
| Add Actuator | `/boot-modify add actuator` · `/boot-modify I need health check endpoints` · `/boot-modify add metrics and management endpoints` · `/boot-modify wire up /health` |
| Add Security | `/boot-modify add spring security` · `/boot-modify add authentication` · `/boot-modify I need security` |
| Add multi-arch native CI | `/boot-modify add multi-arch CI` · `/boot-modify build native for ARM64 and x86` · `/boot-modify add GraalVM CI workflow` |
| Add basic Maven CI | `/boot-modify add basic CI workflow` · `/boot-modify add GitHub Actions` · `/boot-modify set up CI for this project` |
| Add a dependency | `/boot-modify add dependency com.example:my-lib:1.0` · `/boot-modify add org.mapstruct:mapstruct` · `/boot-modify I need MapStruct` |
| Remove a dependency | `/boot-modify remove the h2 dependency` · `/boot-modify drop org.postgresql:postgresql` · `/boot-modify remove h2 from the pom` |

### Examples

```
# Java version management
/boot-modify set java version 21
/boot-modify upgrade to Java 21
/boot-modify downgrade to Java 17
/boot-modify use Java 17 for this service

# POM cleanup
/boot-modify clean pom
/boot-modify remove empty fields from pom.xml
/boot-modify normalize the pom

# Native image
/boot-modify add native image support
/boot-modify enable GraalVM native compilation
/boot-modify remove native image support
/boot-modify drop the native-maven-plugin

# Observability and security
/boot-modify add actuator
/boot-modify I need health check and metrics endpoints
/boot-modify add spring security
/boot-modify add authentication to this project

# CI/CD
/boot-modify add basic CI workflow
/boot-modify add GitHub Actions for Maven
/boot-modify add multi-arch CI
/boot-modify build native images for both ARM and x86

# Dependency management
/boot-modify add dependency org.springframework.boot:spring-boot-starter-web
/boot-modify add org.mapstruct:mapstruct
/boot-modify add com.example:my-library:2.1.0
/boot-modify remove the h2 dependency
/boot-modify drop org.postgresql:postgresql from the pom
/boot-modify remove h2

# Code style
/boot-modify add spring format enforcement
/boot-modify enforce Spring Java Format
```

### POM modification guarantee

All POM changes use Maven's own object model (`MavenXpp3Reader`/`MavenXpp3Writer`). No string replacement, no regex, no AI-generated XML. The agent picks which operation to run — it never writes XML directly.

### Generated CI workflow

`/boot-modify add multi-arch CI` creates `.github/workflows/build.yml` with a matrix strategy targeting `ubuntu-latest` and `macos-latest`, using GraalVM Community Edition for native compilation:

```yaml
jobs:
  build:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
      - name: Build native image
        run: ./mvnw -Pnative native:compile -DskipTests
```

`/boot-modify add basic CI` creates a simpler workflow using Temurin JDK and `./mvnw verify`.

---

## Knowledge-directed design

These commands embody the principle that knowledge + structured execution outperforms model choice. The Java version heuristics, SQL dialect detection, GraalVM selection logic, POM cleaning rules — these are curated knowledge encoded deterministically. The AI contributes when genuine reasoning is needed (code generation, open-ended intents); deterministic tools handle everything that can be expressed as a rule.

This is why `/boot-modify set java version 21` costs zero tokens, but `/boot-modify add native image support with custom AOT hints for my reflection-heavy code` uses the full agent with project context.
