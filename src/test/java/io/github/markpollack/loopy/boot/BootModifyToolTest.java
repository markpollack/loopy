package io.github.markpollack.loopy.boot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BootModifyTool} — verifies each {@code @Tool} method
 * applies the correct deterministic POM or file-system mutation.
 */
class BootModifyToolTest {

	@TempDir
	Path tempDir;

	BootModifyTool tool;

	@BeforeEach
	void setUp() {
		tool = new BootModifyTool(tempDir);
	}

	// --- setJavaVersion ---

	@Test
	void setJavaVersionUpdatesProperty() throws Exception {
		writeMinimalPomWithProperty("java.version", "17");

		String result = tool.setJavaVersion(21);

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).contains("<java.version>21</java.version>");
	}

	@Test
	void setJavaVersionFromDifferentStartingVersion() throws Exception {
		writeMinimalPomWithProperty("java.version", "21");

		tool.setJavaVersion(17);

		assertThat(Files.readString(pomFile())).contains("<java.version>17</java.version>")
			.doesNotContain("<java.version>21</java.version>");
	}

	// --- cleanPom ---

	@Test
	void cleanPomRemovesEmptyName() throws Exception {
		writePom("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <name>   </name>
				  <properties><java.version>21</java.version></properties>
				</project>
				""");

		String result = tool.cleanPom();

		assertThat(result).containsIgnoringCase("cleaned");
	}

	// --- addNativeImageSupport ---

	@Test
	void addNativeImageSupportAddPlugin() throws Exception {
		writeMinimalPom();

		String result = tool.addNativeImageSupport();

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).contains("native-maven-plugin");
	}

	// --- removeNativeImageSupport ---

	@Test
	void removeNativeImageSupportRemovesPlugin() throws Exception {
		writePom(pomWithPlugin("org.graalvm.buildtools", "native-maven-plugin"));

		String result = tool.removeNativeImageSupport();

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).doesNotContain("native-maven-plugin");
	}

	// --- addSpringFormat ---

	@Test
	void addSpringFormatAddsPlugin() throws Exception {
		writeMinimalPom();

		String result = tool.addSpringFormat();

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).contains("spring-javaformat-maven-plugin");
	}

	// --- addActuator ---

	@Test
	void addActuatorAddsDependency() throws Exception {
		writeMinimalPom();

		String result = tool.addActuator();

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).contains("spring-boot-starter-actuator");
	}

	// --- addSecurity ---

	@Test
	void addSecurityAddsDependency() throws Exception {
		writeMinimalPom();

		String result = tool.addSecurity();

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).contains("spring-boot-starter-security");
	}

	// --- addMultiArchCi ---

	@Test
	void addMultiArchCiCreatesWorkflowFile() throws Exception {
		writeMinimalPom();

		String result = tool.addMultiArchCi();

		assertThat(result).isNotBlank();
		Path workflow = tempDir.resolve(".github/workflows/build.yml");
		assertThat(workflow).isRegularFile();
		assertThat(Files.readString(workflow)).contains("graalvm").contains("native:compile");
	}

	// --- addBasicCi ---

	@Test
	void addBasicCiCreatesWorkflowFile() throws Exception {
		writeMinimalPom();

		String result = tool.addBasicCi();

		assertThat(result).isNotBlank();
		Path workflow = tempDir.resolve(".github/workflows/build.yml");
		assertThat(workflow).isRegularFile();
		assertThat(Files.readString(workflow)).contains("mvn");
	}

	// --- addDependency ---

	@Test
	void addDependencyWithoutVersion() throws Exception {
		writeMinimalPom();

		String result = tool.addDependency("org.springframework.boot", "spring-boot-starter-web", null);

		assertThat(result).isNotBlank();
		String pom = Files.readString(pomFile());
		assertThat(pom).contains("spring-boot-starter-web");
	}

	@Test
	void addDependencyWithVersion() throws Exception {
		writeMinimalPom();

		String result = tool.addDependency("com.example", "my-lib", "1.2.3");

		assertThat(result).isNotBlank();
		String pom = Files.readString(pomFile());
		assertThat(pom).contains("my-lib").contains("1.2.3");
	}

	// --- removeDependency ---

	@Test
	void removeDependencyRemovesDep() throws Exception {
		writePom(pomWithDependency("org.springframework.boot", "spring-boot-starter-web"));

		String result = tool.removeDependency("org.springframework.boot", "spring-boot-starter-web");

		assertThat(result).isNotBlank();
		assertThat(Files.readString(pomFile())).doesNotContain("spring-boot-starter-web");
	}

	// --- missing pom ---

	@Test
	void missingPomReturnsError() {
		// No pom.xml written — tool should return a clear error, not throw
		String result = tool.setJavaVersion(21);
		assertThat(result).containsIgnoringCase("pom.xml");
	}

	// --- helpers ---

	private Path pomFile() {
		return tempDir.resolve("pom.xml");
	}

	private void writeMinimalPom() throws IOException {
		writePom("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties><java.version>21</java.version></properties>
				</project>
				""");
	}

	private void writeMinimalPomWithProperty(String key, String value) throws IOException {
		writePom("""
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties><%s>%s</%s></properties>
				</project>
				""".formatted(key, value, key));
	}

	private void writePom(String content) throws IOException {
		Files.writeString(pomFile(), content);
	}

	private String pomWithPlugin(String groupId, String artifactId) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties><java.version>21</java.version></properties>
				  <build>
				    <plugins>
				      <plugin>
				        <groupId>%s</groupId>
				        <artifactId>%s</artifactId>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""".formatted(groupId, artifactId);
	}

	private String pomWithDependency(String groupId, String artifactId) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties><java.version>21</java.version></properties>
				  <dependencies>
				    <dependency>
				      <groupId>%s</groupId>
				      <artifactId>%s</artifactId>
				    </dependency>
				  </dependencies>
				</project>
				""".formatted(groupId, artifactId);
	}

}
