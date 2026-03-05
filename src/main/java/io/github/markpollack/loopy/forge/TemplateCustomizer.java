package io.github.markpollack.loopy.forge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs deterministic template customization — no LLM needed.
 *
 * <p>
 * Handles: package rename (file moves + declaration/import rewrites), pom.xml GAV
 * updates, file generation (dataset, config, prompts, knowledge).
 * </p>
 */
public class TemplateCustomizer {

	private static final Logger logger = LoggerFactory.getLogger(TemplateCustomizer.class);

	private static final String TEMPLATE_PACKAGE = "com.example.experiment";

	private static final String TEMPLATE_GROUP_ID = "com.example";

	private static final String TEMPLATE_ARTIFACT_ID = "agent-experiment-template";

	private static final String TEMPLATE_INVOKER = "TemplateAgentInvoker";

	/**
	 * Apply all deterministic customizations to the cloned template.
	 */
	public void customize(ExperimentBrief brief, Path projectDir) {
		logger.info("Applying deterministic customizations...");

		refactorPackage(brief, projectDir);
		updatePom(brief, projectDir);
		renameAgentInvoker(brief, projectDir);
		generateDatasetItems(brief, projectDir);
		generateExperimentConfig(brief, projectDir);
		generatePromptPlaceholders(brief, projectDir);
		generateKnowledgeFiles(brief, projectDir);
		renameTemplateFiles(projectDir);
		generateReadme(brief, projectDir);

		logger.info("Deterministic customization complete.");
	}

	private void refactorPackage(ExperimentBrief brief, Path projectDir) {
		String targetPackage = brief.packageName();
		logger.info("Refactoring package: {} → {}", TEMPLATE_PACKAGE, targetPackage);

		Path srcMain = projectDir.resolve("src/main/java");
		Path srcTest = projectDir.resolve("src/test/java");

		for (Path srcRoot : List.of(srcMain, srcTest)) {
			if (!Files.exists(srcRoot)) {
				continue;
			}
			Path oldPackageDir = srcRoot.resolve(TEMPLATE_PACKAGE.replace('.', '/'));
			Path newPackageDir = srcRoot.resolve(targetPackage.replace('.', '/'));

			if (Files.exists(oldPackageDir)) {
				movePackageDirectory(oldPackageDir, newPackageDir, srcRoot);
			}
		}

		// Rewrite package declarations and imports in all Java files
		rewriteJavaFiles(projectDir, TEMPLATE_PACKAGE, targetPackage);
	}

	private void movePackageDirectory(Path oldDir, Path newDir, Path srcRoot) {
		try {
			Files.createDirectories(newDir.getParent());
			// Move each file individually (handles nested packages)
			Files.walkFileTree(oldDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path relative = oldDir.relativize(file);
					Path target = newDir.resolve(relative);
					Files.createDirectories(target.getParent());
					Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
					logger.debug("Moved: {} → {}", file, target);
					return FileVisitResult.CONTINUE;
				}
			});
			// Clean up empty directories (including parent chain up to srcRoot)
			deleteEmptyDirectories(oldDir);
			cleanEmptyParents(oldDir.getParent(), srcRoot);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to move package directory", ex);
		}
	}

	private void deleteEmptyDirectories(Path dir) throws IOException {
		// Walk bottom-up and delete empty dirs
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
				try (var entries = Files.list(d)) {
					if (entries.findAny().isEmpty()) {
						Files.delete(d);
					}
				}
				return FileVisitResult.CONTINUE;
			}
		});
		// Try to delete the root too if empty
		if (Files.exists(dir)) {
			try (var entries = Files.list(dir)) {
				if (entries.findAny().isEmpty()) {
					Files.delete(dir);
				}
			}
		}
	}

	private void cleanEmptyParents(Path dir, Path stopAt) throws IOException {
		while (dir != null && !dir.equals(stopAt) && Files.exists(dir)) {
			try (var entries = Files.list(dir)) {
				if (entries.findAny().isEmpty()) {
					Files.delete(dir);
					dir = dir.getParent();
				}
				else {
					break;
				}
			}
		}
	}

	private void rewriteJavaFiles(Path projectDir, String oldPackage, String newPackage) {
		try {
			Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".java")) {
						rewriteFile(file, oldPackage, newPackage);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to rewrite Java files", ex);
		}
	}

	private void rewriteFile(Path file, String oldPackage, String newPackage) throws IOException {
		String content = Files.readString(file, StandardCharsets.UTF_8);
		String updated = content.replace("package " + oldPackage, "package " + newPackage)
			.replace("import " + oldPackage, "import " + newPackage);
		if (!content.equals(updated)) {
			Files.writeString(file, updated, StandardCharsets.UTF_8);
			logger.debug("Rewrote: {}", file);
		}
	}

	private void replaceInJavaFiles(Path projectDir, String oldText, String newText) {
		try {
			Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".java")) {
						String content = Files.readString(file, StandardCharsets.UTF_8);
						String updated = content.replace(oldText, newText);
						if (!content.equals(updated)) {
							Files.writeString(file, updated, StandardCharsets.UTF_8);
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to replace in Java files", ex);
		}
	}

	private void updatePom(ExperimentBrief brief, Path projectDir) {
		Path pomFile = projectDir.resolve("pom.xml");
		logger.info("Updating pom.xml GAV: {}:{}", brief.groupId(), brief.artifactId());

		try {
			String content = Files.readString(pomFile, StandardCharsets.UTF_8);
			content = content
				.replace("<groupId>" + TEMPLATE_GROUP_ID + "</groupId>", "<groupId>" + brief.groupId() + "</groupId>")
				.replace("<artifactId>" + TEMPLATE_ARTIFACT_ID + "</artifactId>",
						"<artifactId>" + brief.artifactId() + "</artifactId>")
				.replace("<name>Agent Experiment Template</name>", "<name>" + brief.name() + "</name>")
				.replace(TEMPLATE_PACKAGE + ".ExperimentApp", brief.packageName() + ".ExperimentApp");
			Files.writeString(pomFile, content, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to update pom.xml", ex);
		}
	}

	private void renameAgentInvoker(ExperimentBrief brief, Path projectDir) {
		String domain = brief.domainName();
		String newName = domain + "AgentInvoker";
		logger.info("Renaming AgentInvoker: {} → {}", TEMPLATE_INVOKER, newName);

		Path srcMain = projectDir.resolve("src/main/java");
		Path newPackageDir = srcMain.resolve(brief.packageName().replace('.', '/'));

		// Rename all three invoker files: Template, AbstractTemplate, TwoPhaseTemplate
		renameInvokerFile(newPackageDir, TEMPLATE_INVOKER, newName, brief);
		renameInvokerFile(newPackageDir, "AbstractTemplateAgentInvoker", "Abstract" + newName, null);
		renameInvokerFile(newPackageDir, "TwoPhaseTemplateAgentInvoker", "TwoPhase" + newName, null);

		// Rewrite references to old class names in all Java files
		// Order matters: longest names first to avoid partial replacement
		replaceInJavaFiles(projectDir, "AbstractTemplateAgentInvoker", "Abstract" + newName);
		replaceInJavaFiles(projectDir, "TwoPhaseTemplateAgentInvoker", "TwoPhase" + newName);
		replaceInJavaFiles(projectDir, TEMPLATE_INVOKER, newName);
	}

	private void renameInvokerFile(Path packageDir, String oldName, String newName, ExperimentBrief brief) {
		Path oldFile = packageDir.resolve(oldName + ".java");
		Path newFile = packageDir.resolve(newName + ".java");

		if (!Files.exists(oldFile)) {
			return;
		}

		try {
			String content = Files.readString(oldFile, StandardCharsets.UTF_8);
			content = content.replace(oldName, newName);

			// For the main invoker, replace javadoc with domain-specific one
			if (brief != null) {
				int classIndex = content.indexOf("public class " + newName);
				if (classIndex > 0) {
					int javadocEnd = content.lastIndexOf("*/", classIndex);
					if (javadocEnd > 0) {
						int javadocStart = content.lastIndexOf("/**", javadocEnd);
						if (javadocStart >= 0) {
							String replacement = "/**\n * " + brief.agent().description() + "\n * Goal: "
									+ brief.agent().goal()
									+ "\n * TODO: Implement domain-specific invocation logic\n */";
							content = content.substring(0, javadocStart) + replacement
									+ content.substring(javadocEnd + 2);
						}
					}
				}
			}
			Files.writeString(newFile, content, StandardCharsets.UTF_8);
			Files.delete(oldFile);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to rename " + oldName, ex);
		}
	}

	private static final List<ExperimentBrief.DatasetItemConfig> DEFAULT_STARTER_REPOS = List.of(
			new ExperimentBrief.DatasetItemConfig("gs-rest-service",
					"https://github.com/spring-guides/gs-rest-service.git", "complete"),
			new ExperimentBrief.DatasetItemConfig("gs-accessing-data-jpa",
					"https://github.com/spring-guides/gs-accessing-data-jpa.git", "complete"),
			new ExperimentBrief.DatasetItemConfig("gs-securing-web",
					"https://github.com/spring-guides/gs-securing-web.git", "complete"));

	private void generateDatasetItems(ExperimentBrief brief, Path projectDir) {
		List<ExperimentBrief.DatasetItemConfig> items = brief.benchmark().dataset();
		if (items.isEmpty()) {
			items = DEFAULT_STARTER_REPOS;
			logger.info("No dataset specified in brief — using {} default Spring Guide repos", items.size());
		}

		Path itemsFile = projectDir.resolve("dataset/items.yaml");
		logger.info("Generating dataset/items.yaml with {} items", items.size());

		StringBuilder sb = new StringBuilder();
		sb.append("items:\n");
		for (ExperimentBrief.DatasetItemConfig item : items) {
			sb.append("  - id: \"").append(item.name()).append("\"\n");
			sb.append("    slug: \"").append(item.name()).append("\"\n");
			sb.append("    task: \"").append(brief.benchmark().task()).append("\"\n");
			sb.append("    source: \"").append(item.url()).append("\"\n");
			sb.append("    subdirectory: \"").append(item.subdirectory()).append("\"\n");
			sb.append("    knowledgeRefs: []\n");
		}

		try {
			Files.createDirectories(itemsFile.getParent());
			Files.writeString(itemsFile, sb.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to generate dataset items", ex);
		}
	}

	private void generateExperimentConfig(ExperimentBrief brief, Path projectDir) {
		Path configFile = projectDir.resolve("experiment-config.yaml");
		logger.info("Generating experiment-config.yaml");

		String model = brief.model() != null ? brief.model() : "claude-sonnet-4-6";
		int timeout = brief.timeoutMinutes() > 0 ? brief.timeoutMinutes() : 15;

		StringBuilder sb = new StringBuilder();
		sb.append("experimentName: ").append(brief.name()).append("\n");
		sb.append("defaultModel: ").append(model).append("\n");
		sb.append("timeoutMinutes: ").append(timeout).append("\n");
		sb.append("variants:\n");

		if (brief.variants().isEmpty()) {
			// Generate default variant progression: control, variant-a, variant-b
			generateDefaultVariants(sb, brief);
		}
		else {
			for (ExperimentBrief.VariantConfig variant : brief.variants()) {
				sb.append("  - name: ").append(variant.name()).append("\n");
				sb.append("    promptFile: ").append(variant.prompt()).append("\n");
				if (!variant.knowledge().isEmpty()) {
					sb.append("    knowledgeDir: knowledge\n");
					sb.append("    knowledgeFiles:\n");
					for (String kf : variant.knowledge()) {
						sb.append("      - ").append(kf).append("\n");
					}
				}
				else {
					sb.append("    knowledgeDir: null\n");
					sb.append("    knowledgeFiles: []\n");
				}
			}
		}

		try {
			Files.writeString(configFile, sb.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to generate experiment config", ex);
		}
	}

	private void generateDefaultVariants(StringBuilder sb, ExperimentBrief brief) {
		// control — no knowledge, naive prompt
		sb.append("  - name: control\n");
		sb.append("    promptFile: v0-naive.txt\n");
		sb.append("    knowledgeDir: null\n");
		sb.append("    knowledgeFiles: []\n");

		// variant-a — no knowledge, hardened prompt
		sb.append("  - name: variant-a\n");
		sb.append("    promptFile: v1-hardened.txt\n");
		sb.append("    knowledgeDir: null\n");
		sb.append("    knowledgeFiles: []\n");

		// variant-b — with knowledge
		sb.append("  - name: variant-b\n");
		sb.append("    promptFile: v2-with-kb.txt\n");
		sb.append("    knowledgeDir: knowledge\n");
		sb.append("    knowledgeFiles:\n");
		sb.append("      - index.md\n");
	}

	private void generatePromptPlaceholders(ExperimentBrief brief, Path projectDir) {
		Path promptsDir = projectDir.resolve("prompts");

		// Collect prompt files from variants, or use defaults
		List<String> promptFiles;
		if (brief.variants().isEmpty()) {
			promptFiles = List.of("v0-naive.txt", "v1-hardened.txt", "v2-with-kb.txt");
		}
		else {
			promptFiles = brief.variants()
				.stream()
				.map(ExperimentBrief.VariantConfig::prompt)
				.distinct()
				.collect(Collectors.toList());
		}

		logger.info("Generating prompt placeholders: {}", promptFiles);

		String task = brief.benchmark().task();
		String agentDesc = brief.agent().description();
		String agentGoal = brief.agent().goal();

		try {
			Files.createDirectories(promptsDir);
			for (String promptFile : promptFiles) {
				Path file = promptsDir.resolve(promptFile);
				if (!Files.exists(file)) {
					String content = generatePromptContent(promptFile, task, agentDesc, agentGoal);
					Files.writeString(file, content, StandardCharsets.UTF_8);
				}
			}
			// Remove the template default.txt if it's not in the brief
			Path defaultPrompt = promptsDir.resolve("default.txt");
			if (Files.exists(defaultPrompt) && !promptFiles.contains("default.txt")) {
				Files.delete(defaultPrompt);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to generate prompt placeholders", ex);
		}
	}

	private String generatePromptContent(String promptFile, String task, String agentDesc, String agentGoal) {
		if (promptFile.contains("v0") || promptFile.contains("naive")) {
			return "You are an AI agent. " + task + "\n\nComplete it in the workspace provided.\n";
		}
		if (promptFile.contains("v1") || promptFile.contains("hardened")) {
			return "You are an expert software engineer. " + task + "\n\n" + "## Context\n\n" + agentDesc + "\nGoal: "
					+ agentGoal + "\n\n" + "## Process\n\n"
					+ "1. **Read first** — Read the project structure, build config, and all source code before making changes.\n"
					+ "2. **Plan** — Identify what needs to change and in what order.\n"
					+ "3. **Implement** — Make changes incrementally, verifying each step compiles.\n"
					+ "4. **Verify** — Run `./mvnw clean test` to confirm everything passes.\n"
					+ "5. **Review** — Check your changes for correctness, edge cases, and style consistency.\n\n"
					+ "## Constraints\n\n" + "- Do NOT introduce breaking changes to existing functionality\n"
					+ "- Follow the project's existing code style and conventions\n"
					+ "- Commit only working code — verify before finishing\n";
		}
		if (promptFile.contains("v2") || promptFile.contains("kb") || promptFile.contains("knowledge")) {
			return "You are an expert software engineer. " + task + "\n\n" + "## Context\n\n" + agentDesc + "\nGoal: "
					+ agentGoal + "\n\n" + "## Knowledge Base\n\n"
					+ "Before making any changes, read all files in the `knowledge/` directory. These contain\n"
					+ "domain-specific guidance, patterns, and best practices that you MUST follow.\n\n"
					+ "Start by reading `knowledge/index.md` for a routing table of available knowledge files,\n"
					+ "then read only the files relevant to your current task.\n\n" + "## Process\n\n"
					+ "1. **Read knowledge** — Read `knowledge/index.md` and follow the routing table.\n"
					+ "2. **Read project** — Read the project structure, build config, and all source code.\n"
					+ "3. **Plan** — Identify what needs to change, informed by knowledge files.\n"
					+ "4. **Implement** — Make changes incrementally, applying knowledge-base patterns.\n"
					+ "5. **Verify** — Run `./mvnw clean test` to confirm everything passes.\n"
					+ "6. **Review** — Check your changes against the knowledge-base guidance.\n\n"
					+ "## Constraints\n\n" + "- Do NOT introduce breaking changes to existing functionality\n"
					+ "- Follow patterns from the knowledge base, not just general best practices\n"
					+ "- Commit only working code — verify before finishing\n";
		}
		// Fallback for custom prompt file names
		return "# TODO: Write prompt for " + promptFile + "\n\n" + "Task: " + task + "\n" + "Agent: " + agentDesc + "\n"
				+ "Goal: " + agentGoal + "\n";
	}

	private void generateKnowledgeFiles(ExperimentBrief brief, Path projectDir) {
		Path knowledgeDir = projectDir.resolve("knowledge");
		logger.info("Generating knowledge files: {}", brief.knowledge().files());

		try {
			Files.createDirectories(knowledgeDir);
			Files.createDirectories(knowledgeDir.resolve("domain"));

			// Generate index.md with proper JIT navigation
			StringBuilder index = new StringBuilder();
			index.append("# Knowledge Base\n\n");
			index.append("> JIT navigation — read only what you need, when you need it.\n\n");
			index.append("## How to Use This Routing Table\n\n");
			index.append("1. Scan the table below to find files relevant to your current task\n");
			index.append("2. Read only the files that match your situation — do NOT read everything\n");
			index.append("3. Follow the guidance in each file as authoritative domain knowledge\n\n");
			index.append("| File | Read when... |\n");
			index.append("|------|-------------|\n");
			for (String file : brief.knowledge().files()) {
				index.append("| `").append(file).append("` | TODO: describe when to read |\n");
			}
			if (brief.knowledge().files().isEmpty()) {
				index.append("| `domain/` | _(add domain-specific knowledge files here)_ |\n");
			}
			index.append("\n## Structure\n\n");
			index.append("Knowledge files are the **independent variable** in ablation experiments — adding\n");
			index.append("or removing knowledge files between variants is how you measure knowledge impact.\n");
			Files.writeString(knowledgeDir.resolve("index.md"), index.toString(), StandardCharsets.UTF_8);

			// Generate placeholder files
			for (String file : brief.knowledge().files()) {
				Path kbFile = knowledgeDir.resolve(file);
				Files.createDirectories(kbFile.getParent());
				if (!Files.exists(kbFile)) {
					String title = file.replace(".md", "").replace("-", " ").replace("/", " — ");
					Files.writeString(kbFile, "# " + title + "\n\nTODO: Write content\n", StandardCharsets.UTF_8);
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to generate knowledge files", ex);
		}
	}

	private void generateReadme(ExperimentBrief brief, Path projectDir) {
		Path readmeFile = projectDir.resolve("README.md");
		logger.info("Generating README.md");

		List<ExperimentBrief.DatasetItemConfig> items = brief.benchmark().dataset();
		if (items.isEmpty()) {
			items = DEFAULT_STARTER_REPOS;
		}

		// Determine variant names for Quick Start examples
		List<String> variantNames;
		if (brief.variants().isEmpty()) {
			variantNames = List.of("control", "variant-a", "variant-b");
		}
		else {
			variantNames = brief.variants().stream().map(ExperimentBrief.VariantConfig::name).toList();
		}

		String firstVariant = variantNames.get(0);
		String firstItem = items.get(0).name();

		StringBuilder sb = new StringBuilder();
		sb.append("# ").append(brief.name()).append("\n\n");
		sb.append(brief.agent().description()).append("\n\n");

		// Quick Start
		sb.append("## Quick Start\n\n");
		sb.append("```bash\n");
		sb.append("# 1. Run the first prompt version against all repos\n");
		sb.append("./mvnw compile exec:java -Dexec.args=\"--variant ").append(firstVariant).append("\"\n\n");
		sb.append("# 2. See the results\n");
		sb.append("./mvnw compile exec:java -Dexec.args=\"--summary\"\n\n");
		if (variantNames.size() > 1) {
			sb.append("# 3. Run a better prompt version\n");
			sb.append("./mvnw compile exec:java -Dexec.args=\"--variant ").append(variantNames.get(1)).append("\"\n\n");
			sb.append("# 4. Compare the two runs\n");
			sb.append("./mvnw compile exec:java -Dexec.args=\"--compare\"\n\n");
		}
		sb.append("# Run all prompt versions in sequence\n");
		sb.append("./mvnw compile exec:java -Dexec.args=\"--run-all-variants\"\n");
		sb.append("```\n\n");
		sb.append("**Tip:** Test with a single repo first to iterate fast:\n");
		sb.append("```bash\n");
		sb.append("./mvnw compile exec:java -Dexec.args=\"--variant ")
			.append(firstVariant)
			.append(" --item ")
			.append(firstItem)
			.append("\"\n");
		sb.append("```\n\n");

		// What's in this project
		sb.append("## What's in This Project\n\n");
		sb.append("```\n");
		sb.append("├── experiment-config.yaml      # Which prompt versions to run and in what order\n");
		sb.append("├── dataset/items.yaml          # Code repos your agent will work on\n");
		sb.append("├── prompts/                    # Prompt files — one per version\n");
		sb.append("├── knowledge/                  # Domain knowledge files the agent can read\n");
		sb.append("│   ├── index.md                # Routing table\n");
		sb.append("│   └── domain/                 # Your domain-specific guidance\n");
		sb.append("├── results/                    # Raw results (generated after runs)\n");
		sb.append("├── analysis/                   # Comparison reports (generated)\n");
		sb.append("└── src/main/java/              # Experiment wiring (rarely needs editing)\n");
		sb.append("```\n\n");

		// Code repos
		sb.append("## Code Repos\n\n");
		sb.append("Your agent will be tested against these repositories:\n\n");
		for (ExperimentBrief.DatasetItemConfig item : items) {
			sb.append("- **").append(item.name()).append("**");
			if (!item.url().isEmpty()) {
				sb.append(" — `").append(item.url()).append("`");
			}
			if (!item.subdirectory().equals(".")) {
				sb.append(" (subdirectory: `").append(item.subdirectory()).append("`)");
			}
			sb.append("\n");
		}
		sb.append("\n");

		// How it works
		sb.append("## How It Works\n\n");
		sb.append("Each prompt version (");
		sb.append(String.join(", ", variantNames));
		sb.append(") represents a different strategy for your agent. ");
		sb.append("You run each version against the same set of code repos and compare pass rates.\n\n");
		sb.append("The experiment runner clones each repo into an isolated workspace, hands it to ");
		sb.append("your agent with the prompt, then judges the result. Results are saved so you can ");
		sb.append("compare across runs and see which changes actually helped.\n\n");

		// Next steps
		sb.append("## Next Steps\n\n");
		sb.append("1. **Edit prompts** — Improve the prompt files in `prompts/`\n");
		sb.append("2. **Add knowledge files** — Write guidance in `knowledge/domain/`\n");
		sb.append("3. **Add more repos** — Edit `dataset/items.yaml`\n");
		sb.append("4. **Add custom judges** — Implement domain-specific judges in `JuryFactory`\n\n");

		// CLI Reference
		sb.append("## CLI Reference\n\n");
		sb.append("| Flag | Description |\n");
		sb.append("|------|-------------|\n");
		sb.append("| `--variant <name>` | Run a single prompt version |\n");
		sb.append("| `--item <slug>` | Filter to a single repo |\n");
		sb.append("| `--run-all-variants` | Run all prompt versions in sequence |\n");
		sb.append("| `--summary` | Print results from the most recent run |\n");
		sb.append("| `--compare` | Compare the two most recent runs |\n");
		sb.append("| `--project-root <path>` | Override project root directory |\n\n");

		// Requirements
		sb.append("## Requirements\n\n");
		sb.append("- Java 17+\n");
		sb.append("- Maven (wrapper included)\n");
		sb.append("- `ANTHROPIC_API_KEY` environment variable\n");

		try {
			Files.writeString(readmeFile, sb.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to generate README.md", ex);
		}
	}

	private void renameTemplateFiles(Path projectDir) {
		Path plansDir = projectDir.resolve("plans");

		renameIfExists(plansDir.resolve("VISION-TEMPLATE.md"), plansDir.resolve("VISION.md"));
		renameIfExists(plansDir.resolve("DESIGN-TEMPLATE.md"), plansDir.resolve("DESIGN.md"));
		renameIfExists(plansDir.resolve("ROADMAP-TEMPLATE.md"), plansDir.resolve("ROADMAP.md"));
	}

	private void renameIfExists(Path from, Path to) {
		try {
			if (Files.exists(from)) {
				Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
				logger.debug("Renamed: {} → {}", from, to);
			}
		}
		catch (IOException ex) {
			logger.warn("Failed to rename {} → {}: {}", from, to, ex.getMessage());
		}
	}

}
