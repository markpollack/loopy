package io.github.markpollack.loopy.boot.modify;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Catalog of deterministic modification recipes for Spring Boot projects.
 *
 * <p>
 * Each recipe maps a named intent to one or more {@link PomMutator} operations or file
 * writes. All recipe execution is fully deterministic — the LLM is only involved in
 * <em>classifying</em> which recipe applies (see {@link RecipeClassifier}), not in
 * executing it.
 * </p>
 *
 * <p>
 * Recipes with no {@code paramNames} require no parameters. Recipes with paramNames
 * require those parameters to be extracted by the classifier (e.g., version number for
 * {@code SET_JAVA_VERSION}, coordinates for {@code ADD_DEPENDENCY}).
 * </p>
 */
public class RecipeCatalog {

	public record Recipe(String name, String description, List<String> paramNames) {
	}

	/** All known recipes, ordered for classifier disambiguation. */
	public static final List<Recipe> ALL = List.of(
			new Recipe("SET_JAVA_VERSION", "Set the Java version (java.version property and compiler properties)",
					List.of("version")),
			new Recipe("CLEAN_POM", "Remove empty/null fields from pom.xml", List.of()),
			new Recipe("ADD_NATIVE_IMAGE", "Add GraalVM native-maven-plugin for native image compilation", List.of()),
			new Recipe("REMOVE_NATIVE_IMAGE", "Remove GraalVM native-maven-plugin", List.of()),
			new Recipe("ADD_SPRING_FORMAT", "Add Spring Java Format enforcement (spring-javaformat-maven-plugin)",
					List.of()),
			new Recipe("ADD_ACTUATOR", "Add spring-boot-starter-actuator (health, metrics, management endpoints)",
					List.of()),
			new Recipe("ADD_SECURITY", "Add spring-boot-starter-security", List.of()),
			new Recipe("ADD_MULTI_ARCH_CI",
					"Generate GitHub Actions workflow for multi-architecture native image builds (ubuntu + macos)",
					List.of()),
			new Recipe("ADD_BASIC_CI", "Generate GitHub Actions workflow for basic Maven CI (build and test)",
					List.of()),
			new Recipe("ADD_DEPENDENCY", "Add a Maven dependency to pom.xml",
					List.of("groupId", "artifactId", "version")),
			new Recipe("REMOVE_DEPENDENCY", "Remove a Maven dependency from pom.xml",
					List.of("groupId", "artifactId")));

	/**
	 * Execute a named recipe.
	 * @param recipeName recipe from {@link #ALL}
	 * @param params extracted parameters (e.g. version, groupId, artifactId)
	 * @param mutator POM mutator bound to the project's pom.xml
	 * @param workDir project root directory
	 * @return human-readable description of what changed
	 */
	public static String execute(String recipeName, Map<String, String> params, PomMutator mutator, Path workDir)
			throws IOException, XmlPullParserException {
		return switch (recipeName) {
			case "SET_JAVA_VERSION" -> {
				String version = params.getOrDefault("version", "21");
				yield mutator.setJavaVersion(version);
			}
			case "CLEAN_POM" -> mutator.cleanPom();
			case "ADD_NATIVE_IMAGE" -> mutator.addPlugin("org.graalvm.buildtools", "native-maven-plugin", null);
			case "REMOVE_NATIVE_IMAGE" -> mutator.removePlugin("org.graalvm.buildtools", "native-maven-plugin");
			case "ADD_SPRING_FORMAT" ->
				mutator.addPlugin("io.spring.javaformat", "spring-javaformat-maven-plugin", null);
			case "ADD_ACTUATOR" ->
				mutator.addDependency("org.springframework.boot", "spring-boot-starter-actuator", null, null);
			case "ADD_SECURITY" ->
				mutator.addDependency("org.springframework.boot", "spring-boot-starter-security", null, null);
			case "ADD_MULTI_ARCH_CI" -> writeWorkflowFile(workDir, "build.yml", multiArchWorkflow());
			case "ADD_BASIC_CI" -> writeWorkflowFile(workDir, "build.yml", basicCiWorkflow());
			case "ADD_DEPENDENCY" -> {
				String gid = params.get("groupId");
				String aid = params.get("artifactId");
				if (gid == null || aid == null)
					yield "Missing groupId or artifactId parameter";
				yield mutator.addDependency(gid, aid, params.get("version"), params.get("scope"));
			}
			case "REMOVE_DEPENDENCY" -> {
				String gid = params.get("groupId");
				String aid = params.get("artifactId");
				if (gid == null || aid == null)
					yield "Missing groupId or artifactId parameter";
				yield mutator.removeDependency(gid, aid);
			}
			default -> throw new IllegalArgumentException("Unknown recipe: " + recipeName);
		};
	}

	// --- File write helpers ---

	private static String writeWorkflowFile(Path workDir, String filename, String content) throws IOException {
		Path dir = workDir.resolve(".github").resolve("workflows");
		Files.createDirectories(dir);
		Path file = dir.resolve(filename);
		boolean existed = Files.exists(file);
		Files.writeString(file, content);
		String relativePath = workDir.relativize(file).toString();
		return (existed ? "Updated " : "Created ") + relativePath;
	}

	// --- CI workflow templates ---

	/**
	 * Multi-architecture native image workflow: builds on ubuntu-latest and macos-latest
	 * using GraalVM Community Edition.
	 */
	static String multiArchWorkflow() {
		return """
				name: Build
				on:
				  push:
				    branches: [ main ]
				  pull_request:
				    branches: [ main ]
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
				""";
	}

	/** Basic Maven CI workflow: build and test on ubuntu-latest with Temurin JDK 21. */
	static String basicCiWorkflow() {
		return """
				name: Build
				on:
				  push:
				    branches: [ main ]
				  pull_request:
				    branches: [ main ]
				jobs:
				  build:
				    runs-on: ubuntu-latest
				    steps:
				      - uses: actions/checkout@v4
				      - uses: actions/setup-java@v4
				        with:
				          java-version: '21'
				          distribution: 'temurin'
				          cache: maven
				      - name: Build and test
				        run: ./mvnw verify
				""";
	}

}
