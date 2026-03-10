package io.github.markpollack.loopy.boot.modify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCatalogTest {

	@TempDir
	Path tempDir;

	@Test
	void catalogHasExpectedRecipes() {
		var names = RecipeCatalog.ALL.stream().map(RecipeCatalog.Recipe::name).toList();
		assertThat(names).contains("SET_JAVA_VERSION", "CLEAN_POM", "ADD_NATIVE_IMAGE", "REMOVE_NATIVE_IMAGE",
				"ADD_SPRING_FORMAT", "ADD_ACTUATOR", "ADD_SECURITY", "ADD_MULTI_ARCH_CI", "ADD_BASIC_CI",
				"ADD_DEPENDENCY", "REMOVE_DEPENDENCY");
	}

	@Test
	void setJavaVersionRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithJavaVersion("17"));
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("SET_JAVA_VERSION", Map.of("version", "21"), mutator, tempDir);

		assertThat(result).contains("21");
		assertThat(Files.readString(tempDir.resolve("pom.xml"))).contains("<java.version>21</java.version>");
	}

	@Test
	void addNativeImageRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_NATIVE_IMAGE", Map.of(), mutator, tempDir);

		assertThat(result).contains("native-maven-plugin");
		assertThat(Files.readString(tempDir.resolve("pom.xml"))).contains("native-maven-plugin");
	}

	@Test
	void removeNativeImageRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithNativePlugin());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("REMOVE_NATIVE_IMAGE", Map.of(), mutator, tempDir);

		assertThat(result).contains("Removed");
		assertThat(Files.readString(tempDir.resolve("pom.xml"))).doesNotContain("native-maven-plugin");
	}

	@Test
	void addActuatorRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_ACTUATOR", Map.of(), mutator, tempDir);

		assertThat(result).contains("spring-boot-starter-actuator");
	}

	@Test
	void addSecurityRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_SECURITY", Map.of(), mutator, tempDir);

		assertThat(result).contains("spring-boot-starter-security");
	}

	@Test
	void addMultiArchCiRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_MULTI_ARCH_CI", Map.of(), mutator, tempDir);

		assertThat(result).contains("build.yml");
		Path workflow = tempDir.resolve(".github/workflows/build.yml");
		assertThat(Files.exists(workflow)).isTrue();
		String content = Files.readString(workflow);
		assertThat(content).contains("graalvm/setup-graalvm");
		assertThat(content).contains("native:compile");
		assertThat(content).contains("ubuntu-latest, macos-latest");
	}

	@Test
	void addBasicCiRecipe() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_BASIC_CI", Map.of(), mutator, tempDir);

		assertThat(result).contains("build.yml");
		Path workflow = tempDir.resolve(".github/workflows/build.yml");
		String content = Files.readString(workflow);
		assertThat(content).contains("actions/setup-java");
		assertThat(content).contains("mvnw verify");
	}

	@Test
	void addDependencyRecipeMissingParams() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_DEPENDENCY", Map.of("groupId", "com.example"), mutator, tempDir);

		assertThat(result).containsIgnoringCase("missing");
	}

	@Test
	void addDependencyRecipeWithCoords() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		var mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = RecipeCatalog.execute("ADD_DEPENDENCY",
				Map.of("groupId", "com.example", "artifactId", "my-lib", "version", "1.0"), mutator, tempDir);

		assertThat(result).contains("com.example:my-lib");
	}

	@Test
	void multiArchWorkflowIsValidYaml() {
		String yaml = RecipeCatalog.multiArchWorkflow();
		assertThat(yaml).startsWith("name:");
		assertThat(yaml).contains("graalvm/setup-graalvm");
		assertThat(yaml).contains("native:compile");
		// Should not have leading spaces/tabs in the first line
		assertThat(yaml.charAt(0)).isEqualTo('n');
	}

	@Test
	void basicCiWorkflowIsValidYaml() {
		String yaml = RecipeCatalog.basicCiWorkflow();
		assertThat(yaml).startsWith("name:");
		assertThat(yaml).contains("actions/setup-java");
		assertThat(yaml.charAt(0)).isEqualTo('n');
	}

	// --- helpers ---

	private String minimalPom() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <dependencies/>
				</project>
				""";
	}

	private String pomWithJavaVersion(String version) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties>
				    <java.version>%s</java.version>
				  </properties>
				  <dependencies/>
				</project>
				""".formatted(version);
	}

	private String pomWithNativePlugin() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <build>
				    <plugins>
				      <plugin>
				        <groupId>org.graalvm.buildtools</groupId>
				        <artifactId>native-maven-plugin</artifactId>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""";
	}

}
