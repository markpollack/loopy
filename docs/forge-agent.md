# Forge Agent Command

The `/forge-agent` slash command scaffolds agent experiment projects from a YAML brief. It clones a template repository, customizes it with your experiment's package names, variants, and dataset, and generates a project-specific README.

## Usage

```
/forge-agent --brief path/to/brief.yaml
/forge-agent --brief brief.yaml --output ~/projects/my-experiment
```

| Flag | Description | Default |
|------|-------------|---------|
| `--brief <path>` | Path to the YAML experiment brief | *required* |
| `--output <path>` | Output directory for the scaffolded project | Current directory + brief name |
| `--no-llm` | Skip LLM-generated content (variant descriptions) | — |
| `--summary` | Print summary of what would be created | — |

## Brief Format

The experiment brief is a YAML file that defines your experiment:

```yaml
name: spring-security-agent
groupId: com.example
artifactId: spring-security-experiment
templateRepo: markpollack/agent-experiment-template

variants:
  - name: baseline
    description: "Agent with no domain knowledge"
  - name: with-knowledge
    description: "Agent with Spring Security KB"

dataset:
  items:
    - name: gs-securing-web
      repo: spring-guides/gs-securing-web
    - name: gs-rest-service
      repo: spring-guides/gs-rest-service
```

### Fields

| Field | Description | Required |
|-------|-------------|----------|
| `name` | Experiment name (used for directory and artifact names) | Yes |
| `groupId` | Maven group ID for the generated project | Yes |
| `artifactId` | Maven artifact ID | Yes |
| `templateRepo` | GitHub repository to clone as template | Yes |
| `variants` | List of experiment variants to compare | Yes |
| `variants[].name` | Variant identifier | Yes |
| `variants[].description` | What this variant tests | No |
| `dataset.items` | List of dataset items (tasks/repos to test against) | No |
| `dataset.items[].name` | Item identifier | Yes |
| `dataset.items[].repo` | Source repository | No |

If `dataset.items` is empty, Loopy generates default items using Spring Guide starter repos (gs-rest-service, gs-accessing-data-jpa, gs-securing-web).

## Customization Pipeline

The `/forge-agent` command runs a 7-step deterministic pipeline:

1. **Clone** — Clone the template repository, strip git history, reinitialize
2. **Package rename** — Update Java package declarations, imports, and move files to match `groupId`
3. **POM updates** — Set `groupId`, `artifactId`, and artifact names in `pom.xml`
4. **Config generation** — Create `ExperimentConfig.yaml` with variants and dataset items
5. **Prompt placeholders** — Generate prompt template files for each variant
6. **README generation** — Create a project-specific README with Quick Start, CLI reference, and variant/item names
7. **Default dataset** — If dataset is empty, generate default items with starter repos

Steps 1-7 are fully deterministic (no LLM). An optional LLM step generates richer variant descriptions when `--no-llm` is not set.

## Example

```
> /forge-agent --brief ~/briefs/coverage.yaml

Scaffolding project from brief: coverage.yaml
  Cloning template: markpollack/agent-experiment-template
  Renaming packages: com.example.coverage
  Updating POM: coverage-experiment
  Generating config: 2 variants, 3 dataset items
  Generating README
  Done! Project at: ~/projects/coverage-experiment
```

The scaffolded project is immediately buildable with `./mvnw package` and includes sample data to run experiments against.
