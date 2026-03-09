package io.github.markpollack.loopy.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight project analyzer that generates a structured report from source code. No
 * external dependencies (no ASM, no SCIP) — just file reading and regex.
 *
 * <p>
 * Produces a {@code PROJECT-ANALYSIS.md} file in the workspace that gives a bounded agent
 * a pre-computed understanding of the project structure, reducing exploratory tool calls
 * during code generation. This is the SAE v2 format with fully-qualified import blocks
 * per test slice.
 * </p>
 *
 * <p>
 * Ported from {@code code-coverage-experiment} {@code ProjectAnalyzer} — pure JDK +
 * SLF4J, no additional dependencies.
 * </p>
 */
public class BootProjectAnalyzer {

	private static final Logger logger = LoggerFactory.getLogger(BootProjectAnalyzer.class);

	private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@(\\w+)(?:\\(([^)]*?)\\))?");

	private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+);",
			Pattern.MULTILINE);

	private static final Pattern CLASS_DECL_PATTERN = Pattern
		.compile("(?:public|protected|private)?\\s*(?:abstract\\s+)?(?:class|interface|enum|record)\\s+(\\w+)");

	private static final Pattern EXTENDS_PATTERN = Pattern.compile("extends\\s+(\\w+)");

	private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("implements\\s+([\\w,\\s]+?)\\s*\\{");

	private BootProjectAnalyzer() {
	}

	/**
	 * Analyze the project at the given workspace path and write
	 * {@code PROJECT-ANALYSIS.md}.
	 */
	public static void analyze(Path workspace) {
		try {
			String report = generateReport(workspace);
			Path outputPath = workspace.resolve("PROJECT-ANALYSIS.md");
			Files.writeString(outputPath, report);
			logger.info("Generated project analysis: {} ({} bytes)", outputPath, report.length());
		}
		catch (IOException ex) {
			logger.warn("Failed to generate project analysis: {}", ex.getMessage());
		}
	}

	public static String generateReport(Path workspace) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("# Project Analysis\n\n");
		sb.append("Auto-generated structural analysis. Use this to understand the project before writing code.\n\n");

		// 1. POM analysis
		appendPomAnalysis(sb, workspace);

		// 2. Source file inventory
		List<JavaFileInfo> sourceFiles = scanJavaFiles(workspace.resolve("src/main/java"));
		appendSourceInventory(sb, sourceFiles, "Production Code");

		// 3. Existing test files (if any)
		List<JavaFileInfo> testFiles = scanJavaFiles(workspace.resolve("src/test/java"));
		if (!testFiles.isEmpty()) {
			appendSourceInventory(sb, testFiles, "Existing Tests");
		}

		// 4. Component classification
		appendComponentClassification(sb, sourceFiles);

		// 5. Recommended test strategy (with import blocks)
		appendTestStrategy(sb, sourceFiles);

		// 6. Config files
		appendConfigFiles(sb, workspace);

		return sb.toString();
	}

	private static void appendPomAnalysis(StringBuilder sb, Path workspace) throws IOException {
		Path pomPath = workspace.resolve("pom.xml");
		if (!Files.isRegularFile(pomPath)) {
			return;
		}

		String pom = Files.readString(pomPath);
		sb.append("## Dependencies & Versions\n\n");

		// Boot version from parent
		String bootVersion = extractXmlValue(pom, "spring-boot-starter-parent");
		if (bootVersion != null) {
			sb.append("- **Spring Boot**: ").append(bootVersion).append("\n");
		}

		// Java version
		String javaVersion = extractProperty(pom, "java.version");
		if (javaVersion != null) {
			sb.append("- **Java**: ").append(javaVersion).append("\n");
		}

		// Key dependencies
		sb.append("\n**Dependencies** (test-relevant):\n");
		List<String[]> deps = extractDependencies(pom);
		for (String[] dep : deps) {
			String groupId = dep[0];
			String artifactId = dep[1];
			if (groupId.contains("springframework") || groupId.contains("spring") || artifactId.contains("test")
					|| artifactId.contains("mock") || artifactId.contains("junit") || artifactId.contains("assertj")
					|| groupId.contains("jakarta") || groupId.contains("javax") || artifactId.contains("h2")
					|| artifactId.contains("hsqldb") || artifactId.contains("jackson")
					|| artifactId.contains("validation") || artifactId.contains("security")
					|| artifactId.contains("websocket") || artifactId.contains("webflux")
					|| artifactId.contains("reactor")) {
				sb.append("- `").append(groupId).append(":").append(artifactId).append("`\n");
			}
		}
		sb.append("\n");
	}

	private static void appendSourceInventory(StringBuilder sb, List<JavaFileInfo> files, String heading) {
		sb.append("## ").append(heading).append("\n\n");
		sb.append("| File | Type | Annotations | Extends/Implements |\n");
		sb.append("|------|------|-------------|--------------------|\n");
		for (JavaFileInfo f : files) {
			String annos = f.annotations.stream()
				.filter(BootProjectAnalyzer::isSignificantAnnotation)
				.collect(Collectors.joining(", "));
			String hierarchy = "";
			if (f.extendsClass != null) {
				hierarchy += "extends " + f.extendsClass;
			}
			if (!f.implementsInterfaces.isEmpty()) {
				if (!hierarchy.isEmpty()) {
					hierarchy += ", ";
				}
				hierarchy += "implements " + String.join(", ", f.implementsInterfaces);
			}
			sb.append("| `")
				.append(f.relativePath)
				.append("` | ")
				.append(f.type)
				.append(" | ")
				.append(annos)
				.append(" | ")
				.append(hierarchy)
				.append(" |\n");
		}
		sb.append("\n");
	}

	private static void appendComponentClassification(StringBuilder sb, List<JavaFileInfo> files) {
		sb.append("## Component Classification\n\n");

		Map<String, List<JavaFileInfo>> categories = new LinkedHashMap<>();
		categories.put("REST Controllers", new ArrayList<>());
		categories.put("Services", new ArrayList<>());
		categories.put("Repositories", new ArrayList<>());
		categories.put("JPA Entities", new ArrayList<>());
		categories.put("Configuration", new ArrayList<>());
		categories.put("Other", new ArrayList<>());

		for (JavaFileInfo f : files) {
			if (f.hasAnnotation("RestController") || f.hasAnnotation("Controller")) {
				categories.get("REST Controllers").add(f);
			}
			else if (f.hasAnnotation("Service")) {
				categories.get("Services").add(f);
			}
			else if (f.hasAnnotation("Repository") || f.extendsOrImplements("Repository", "CrudRepository",
					"JpaRepository", "PagingAndSortingRepository", "ReactiveCrudRepository")) {
				categories.get("Repositories").add(f);
			}
			else if (f.hasAnnotation("Entity") || f.hasAnnotation("Table")) {
				categories.get("JPA Entities").add(f);
			}
			else if (f.hasAnnotation("Configuration") || f.hasAnnotation("SpringBootApplication")) {
				categories.get("Configuration").add(f);
			}
			else {
				categories.get("Other").add(f);
			}
		}

		for (Map.Entry<String, List<JavaFileInfo>> entry : categories.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				sb.append("### ").append(entry.getKey()).append("\n");
				for (JavaFileInfo f : entry.getValue()) {
					sb.append("- `").append(f.className).append("` — ");
					sb.append(summarizeClass(f)).append("\n");
				}
				sb.append("\n");
			}
		}
	}

	private static void appendTestStrategy(StringBuilder sb, List<JavaFileInfo> files) {
		sb.append("## Recommended Test Strategy (with full imports)\n\n");

		boolean hasControllers = files.stream()
			.anyMatch(f -> f.hasAnnotation("RestController") || f.hasAnnotation("Controller"));
		boolean hasJpa = files.stream()
			.anyMatch(f -> f.hasAnnotation("Entity") || f.extendsOrImplements("JpaRepository", "CrudRepository"));
		boolean hasServices = files.stream().anyMatch(f -> f.hasAnnotation("Service"));
		boolean hasWebFlux = files.stream()
			.anyMatch(f -> f.imports.stream().anyMatch(i -> i.contains("webflux") || i.contains("reactor")));
		boolean hasSecurity = files.stream().anyMatch(f -> f.imports.stream().anyMatch(i -> i.contains("security")));
		boolean hasWebSocket = files.stream()
			.anyMatch(f -> f.imports.stream().anyMatch(i -> i.contains("websocket") || i.contains("stomp")));

		if (hasControllers && !hasWebFlux) {
			sb.append("### Controller Tests (MVC)\n");
			sb.append(
					"Use `@WebMvcTest(XxxController.class)` with `MockMvc`. Mock service dependencies with `@MockitoBean`.\n");
			sb.append("```java\n");
			sb.append("import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;\n");
			sb.append("import org.springframework.test.web.servlet.MockMvc;\n");
			sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
			sb.append("import org.springframework.test.context.bean.override.mockito.MockitoBean;\n");
			sb.append("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;\n");
			sb.append("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;\n");
			sb.append("```\n\n");
		}
		if (hasControllers && hasWebFlux) {
			sb.append("### Controller Tests (WebFlux)\n");
			sb.append("Use `@WebFluxTest(XxxController.class)` with `WebTestClient`.\n");
			sb.append("```java\n");
			sb.append("import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;\n");
			sb.append("import org.springframework.test.web.reactive.server.WebTestClient;\n");
			sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
			sb.append("import org.springframework.test.context.bean.override.mockito.MockitoBean;\n");
			sb.append("```\n\n");
		}
		if (hasJpa) {
			sb.append("### Repository / JPA Tests\n");
			sb.append("Use `@DataJpaTest` with `TestEntityManager`. Tests auto-rollback.\n");
			sb.append("```java\n");
			sb.append("import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;\n");
			sb.append("import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;\n");
			sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
			sb.append("```\n\n");
		}
		if (hasServices) {
			sb.append("### Service Tests\n");
			sb.append("Plain JUnit + Mockito — no Spring context needed.\n");
			sb.append("```java\n");
			sb.append("import org.junit.jupiter.api.Test;\n");
			sb.append("import org.junit.jupiter.api.extension.ExtendWith;\n");
			sb.append("import org.mockito.InjectMocks;\n");
			sb.append("import org.mockito.Mock;\n");
			sb.append("import org.mockito.junit.jupiter.MockitoExtension;\n");
			sb.append("import static org.mockito.Mockito.*;\n");
			sb.append("import static org.assertj.core.api.Assertions.*;\n");
			sb.append("```\n\n");
		}
		if (hasSecurity) {
			sb.append("### Security Tests\n");
			sb.append("Use `@WithMockUser` for authenticated endpoints.\n");
			sb.append("```java\n");
			sb.append("import org.springframework.security.test.context.support.WithMockUser;\n");
			sb.append(
					"import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;\n");
			sb.append("```\n\n");
		}
		if (hasWebSocket) {
			sb.append("### WebSocket Tests\n");
			sb.append("Use `@SpringBootTest(webEnvironment=RANDOM_PORT)` or test message handling directly.\n");
			sb.append("```java\n");
			sb.append("import org.springframework.boot.test.context.SpringBootTest;\n");
			sb.append("import org.springframework.boot.test.web.server.LocalServerPort;\n");
			sb.append("```\n\n");
		}
	}

	private static void appendConfigFiles(StringBuilder sb, Path workspace) throws IOException {
		Path resourcesDir = workspace.resolve("src/main/resources");
		if (!Files.isDirectory(resourcesDir)) {
			return;
		}

		List<String> configFiles = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(resourcesDir)) {
			stream.filter(Files::isRegularFile)
				.map(p -> workspace.relativize(p).toString())
				.filter(p -> p.endsWith(".properties") || p.endsWith(".yml") || p.endsWith(".yaml")
						|| p.endsWith(".xml") || p.endsWith(".sql"))
				.forEach(configFiles::add);
		}

		if (!configFiles.isEmpty()) {
			sb.append("## Configuration Files\n\n");
			for (String f : configFiles) {
				sb.append("- `").append(f).append("`\n");
			}
			sb.append("\n");
		}
	}

	// --- File scanning ---

	static List<JavaFileInfo> scanJavaFiles(Path sourceDir) throws IOException {
		if (!Files.isDirectory(sourceDir)) {
			return List.of();
		}

		List<JavaFileInfo> files = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(sourceDir)) {
			List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();

			for (Path javaFile : javaFiles) {
				try {
					String content = Files.readString(javaFile);
					String relativePath = sourceDir.getParent().getParent().getParent().relativize(javaFile).toString();
					files.add(parseJavaFile(content, relativePath));
				}
				catch (IOException ex) {
					logger.debug("Failed to read {}: {}", javaFile, ex.getMessage());
				}
			}
		}
		return files;
	}

	static JavaFileInfo parseJavaFile(String content, String relativePath) {
		String className = relativePath.replaceAll(".*/", "").replace(".java", "");

		List<String> imports = new ArrayList<>();
		Matcher importMatcher = IMPORT_PATTERN.matcher(content);
		while (importMatcher.find()) {
			imports.add(importMatcher.group(2));
		}

		List<String> annotations = new ArrayList<>();
		Matcher annoMatcher = ANNOTATION_PATTERN.matcher(content);
		while (annoMatcher.find()) {
			annotations.add(annoMatcher.group(1));
		}

		String type = "class";
		Matcher classMatcher = CLASS_DECL_PATTERN.matcher(content);
		if (classMatcher.find()) {
			// Use the matched text to identify the keyword (works with/without access
			// modifiers)
			String matchText = classMatcher.group(0);
			if (matchText.contains("interface ")) {
				type = "interface";
			}
			else if (matchText.contains("enum ")) {
				type = "enum";
			}
			else if (matchText.contains("record ")) {
				type = "record";
			}
		}

		String extendsClass = null;
		Matcher extMatcher = EXTENDS_PATTERN.matcher(content);
		if (extMatcher.find()) {
			extendsClass = extMatcher.group(1);
		}

		List<String> implementsInterfaces = new ArrayList<>();
		Matcher implMatcher = IMPLEMENTS_PATTERN.matcher(content);
		if (implMatcher.find()) {
			String implStr = implMatcher.group(1);
			for (String iface : implStr.split(",")) {
				String trimmed = iface.trim().replaceAll("<.*>", "");
				if (!trimmed.isEmpty()) {
					implementsInterfaces.add(trimmed);
				}
			}
		}

		List<String> methods = new ArrayList<>();
		Pattern methodPattern = Pattern.compile("(?:public|protected)\\s+\\S+\\s+(\\w+)\\s*\\(");
		Matcher methodMatcher = methodPattern.matcher(content);
		while (methodMatcher.find()) {
			String name = methodMatcher.group(1);
			if (!name.equals(className)) {
				methods.add(name);
			}
		}

		return new JavaFileInfo(relativePath, className, type, imports, annotations, extendsClass, implementsInterfaces,
				methods);
	}

	// --- Helpers ---

	private static boolean isSignificantAnnotation(String annotation) {
		return Set.of("RestController", "Controller", "Service", "Repository", "Component", "Entity", "Table",
				"Configuration", "SpringBootApplication", "RequestMapping", "GetMapping", "PostMapping", "PutMapping",
				"DeleteMapping", "Autowired", "Value", "Bean", "EnableWebSecurity", "EnableMethodSecurity",
				"Transactional", "Id", "GeneratedValue", "ManyToOne", "OneToMany", "MappedSuperclass", "Validated",
				"Valid", "EnableWebSocketMessageBroker", "MessageMapping", "SendTo")
			.contains(annotation);
	}

	private static String summarizeClass(JavaFileInfo f) {
		List<String> parts = new ArrayList<>();
		if (!f.methods.isEmpty()) {
			parts.add(f.methods.size() + " public methods");
			List<String> preview = f.methods.subList(0, Math.min(3, f.methods.size()));
			parts.add("(" + String.join(", ", preview) + (f.methods.size() > 3 ? ", ..." : "") + ")");
		}
		return String.join(" ", parts);
	}

	private static String extractXmlValue(String pom, String artifactId) {
		int idx = pom.indexOf("<artifactId>" + artifactId + "</artifactId>");
		if (idx < 0) {
			return null;
		}
		int vStart = pom.indexOf("<version>", idx);
		int vEnd = pom.indexOf("</version>", vStart);
		if (vStart < 0 || vEnd < 0 || vStart > idx + 200) {
			return null;
		}
		return pom.substring(vStart + 9, vEnd);
	}

	private static String extractProperty(String pom, String propertyName) {
		String tag = "<" + propertyName + ">";
		int start = pom.indexOf(tag);
		if (start < 0) {
			return null;
		}
		int end = pom.indexOf("</" + propertyName + ">", start);
		if (end < 0) {
			return null;
		}
		return pom.substring(start + tag.length(), end);
	}

	private static List<String[]> extractDependencies(String pom) {
		List<String[]> deps = new ArrayList<>();
		Pattern depPattern = Pattern
			.compile("<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);
		Matcher m = depPattern.matcher(pom);
		while (m.find()) {
			deps.add(new String[] { m.group(1), m.group(2) });
		}
		return deps;
	}

	// --- Data class ---

	record JavaFileInfo(String relativePath, String className, String type, List<String> imports,
			List<String> annotations, String extendsClass, List<String> implementsInterfaces, List<String> methods) {

		boolean hasAnnotation(String name) {
			return annotations.contains(name);
		}

		boolean extendsOrImplements(String... names) {
			Set<String> nameSet = Set.of(names);
			if (extendsClass != null && nameSet.contains(extendsClass)) {
				return true;
			}
			for (String iface : implementsInterfaces) {
				if (nameSet.contains(iface)) {
					return true;
				}
			}
			return false;
		}

	}

}
