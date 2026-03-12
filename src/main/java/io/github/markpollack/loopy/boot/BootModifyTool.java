package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.boot.modify.PomMutator;
import io.github.markpollack.loopy.boot.modify.RecipeCatalog;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent tools for structural modification of an existing Spring Boot project.
 *
 * <p>
 * Each {@code @Tool} method handles one modification. The agent selects the right method
 * from the descriptions — no homebrew NL classification needed.
 * </p>
 *
 * <p>
 * All mutations go through {@link PomMutator} (Maven object model round-trip) — no string
 * templates, no regex.
 * </p>
 */
public class BootModifyTool {

	private static final Logger logger = LoggerFactory.getLogger(BootModifyTool.class);

	private final Path workingDirectory;

	public BootModifyTool(Path workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	@Tool(description = "Set the Java version in the project's pom.xml (java.version property and compiler plugin)")
	public String setJavaVersion(@ToolParam(description = "Java major version number, e.g. 21 or 17") int version) {
		return withMutator(mutator -> RecipeCatalog.execute("SET_JAVA_VERSION",
				java.util.Map.of("javaVersion", String.valueOf(version)), mutator, workingDirectory));
	}

	@Tool(description = "Clean pom.xml by removing empty, null, or redundant fields — normalizes formatting")
	public String cleanPom() {
		return withMutator(
				mutator -> RecipeCatalog.execute("CLEAN_POM", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add GraalVM native-maven-plugin to enable native image compilation")
	public String addNativeImageSupport() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_NATIVE_IMAGE", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Remove GraalVM native-maven-plugin from the project")
	public String removeNativeImageSupport() {
		return withMutator(
				mutator -> RecipeCatalog.execute("REMOVE_NATIVE_IMAGE", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add Spring Java Format enforcement (spring-javaformat-maven-plugin) to the project")
	public String addSpringFormat() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_SPRING_FORMAT", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add spring-boot-starter-actuator for health checks, metrics, and management endpoints")
	public String addActuator() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_ACTUATOR", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add spring-boot-starter-security to the project")
	public String addSecurity() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_SECURITY", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add a GitHub Actions CI workflow for multi-arch native image builds")
	public String addMultiArchCi() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_MULTI_ARCH_CI", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add a GitHub Actions CI workflow for standard Maven build and test")
	public String addBasicCi() {
		return withMutator(
				mutator -> RecipeCatalog.execute("ADD_BASIC_CI", java.util.Map.of(), mutator, workingDirectory));
	}

	@Tool(description = "Add a Maven dependency to pom.xml")
	public String addDependency(
			@ToolParam(description = "Maven groupId of the dependency, e.g. org.springframework.boot") String groupId,
			@ToolParam(
					description = "Maven artifactId of the dependency, e.g. spring-boot-starter-web") String artifactId,
			@ToolParam(description = "Version string. Omit if managed by the Spring Boot BOM.",
					required = false) @Nullable String version) {
		return withMutator(mutator -> RecipeCatalog.execute("ADD_DEPENDENCY", java.util.Map.of("groupId", groupId,
				"artifactId", artifactId, "version", version != null ? version : ""), mutator, workingDirectory));
	}

	@Tool(description = "Remove a Maven dependency from pom.xml")
	public String removeDependency(@ToolParam(description = "Maven groupId of the dependency to remove") String groupId,
			@ToolParam(description = "Maven artifactId of the dependency to remove") String artifactId) {
		return withMutator(mutator -> RecipeCatalog.execute("REMOVE_DEPENDENCY",
				java.util.Map.of("groupId", groupId, "artifactId", artifactId), mutator, workingDirectory));
	}

	// --- helpers ---

	@FunctionalInterface
	private interface PomAction {

		String apply(PomMutator mutator) throws Exception;

	}

	private String withMutator(PomAction action) {
		Path pomFile = workingDirectory.resolve("pom.xml");
		if (!Files.exists(pomFile)) {
			return "No pom.xml found in " + workingDirectory
					+ ". Make sure the working directory is a Maven project root.";
		}
		try {
			PomMutator mutator = new PomMutator(pomFile);
			String result = action.apply(mutator);
			logger.debug("BootModifyTool completed: {}", result);
			return result;
		}
		catch (Exception ex) {
			logger.error("BootModifyTool failed: {}", ex.getMessage(), ex);
			return "Error: " + ex.getMessage();
		}
	}

}
