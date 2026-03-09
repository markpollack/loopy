package io.github.markpollack.loopy.boot.modify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PomMutatorTest {

	@TempDir
	Path tempDir;

	@Test
	void setJavaVersionUpdatesProperty() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithJavaVersion("17"));
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.setJavaVersion("21");

		assertThat(result).contains("21");
		String pom = Files.readString(tempDir.resolve("pom.xml"));
		assertThat(pom).contains("<java.version>21</java.version>");
		assertThat(pom).doesNotContain("<java.version>17</java.version>");
	}

	@Test
	void setJavaVersionNoChangeIfSame() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithJavaVersion("21"));
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.setJavaVersion("21");

		assertThat(result).containsIgnoringCase("no change");
	}

	@Test
	void addDependencyInsertsNewDep() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.addDependency("com.example", "my-lib", "1.0.0", null);

		assertThat(result).contains("my-lib");
		String pom = Files.readString(tempDir.resolve("pom.xml"));
		assertThat(pom).contains("<artifactId>my-lib</artifactId>");
		assertThat(pom).contains("<version>1.0.0</version>");
	}

	@Test
	void addDependencySkipsDuplicate() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		mutator.addDependency("org.springframework.boot", "spring-boot-starter", null, null);
		String result = mutator.addDependency("org.springframework.boot", "spring-boot-starter", null, null);

		assertThat(result).containsIgnoringCase("no change");
	}

	@Test
	void cleanPomRemovesEmptyFields() throws Exception {
		String pomWithEmpties = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <name>  </name>
				  <description></description>
				  <url>  </url>
				  <properties>
				    <java.version>21</java.version>
				  </properties>
				  <dependencies/>
				</project>
				""";
		Files.writeString(tempDir.resolve("pom.xml"), pomWithEmpties);
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.cleanPom();

		assertThat(result).containsIgnoringCase("cleaned");
	}

	@Test
	void addPluginInsertsPlugin() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.addPlugin("org.graalvm.buildtools", "native-maven-plugin", "0.10.3");

		assertThat(result).contains("native-maven-plugin");
		String pom = Files.readString(tempDir.resolve("pom.xml"));
		assertThat(pom).contains("<artifactId>native-maven-plugin</artifactId>");
	}

	@Test
	void removeDependencyDeletesDep() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		PomMutator mutator = new PomMutator(tempDir.resolve("pom.xml"));

		String result = mutator.removeDependency("org.springframework.boot", "spring-boot-starter");

		assertThat(result).containsIgnoringCase("removed");
		String pom = Files.readString(tempDir.resolve("pom.xml"));
		assertThat(pom).doesNotContain("spring-boot-starter</artifactId>");
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

	private String minimalPom() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <dependencies>
				    <dependency>
				      <groupId>org.springframework.boot</groupId>
				      <artifactId>spring-boot-starter</artifactId>
				    </dependency>
				  </dependencies>
				</project>
				""";
	}

}
