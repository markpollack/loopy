package io.github.markpollack.loopy.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BootProjectAnalyzerTest {

	@TempDir
	Path tempDir;

	@Test
	void analyzesProjectAndWritesReport() throws Exception {
		createSampleProject(tempDir);

		BootProjectAnalyzer.analyze(tempDir);

		Path report = tempDir.resolve("PROJECT-ANALYSIS.md");
		assertThat(report).isRegularFile();
		String content = Files.readString(report);
		assertThat(content).contains("# Project Analysis");
		assertThat(content).contains("## Production Code");
		assertThat(content).contains("GreetingController");
		assertThat(content).contains("GreetingService");
	}

	@Test
	void reportIncludesComponentClassification() throws Exception {
		createSampleProject(tempDir);

		String report = BootProjectAnalyzer.generateReport(tempDir);

		assertThat(report).contains("## Component Classification");
		assertThat(report).contains("REST Controllers");
		assertThat(report).contains("Services");
	}

	@Test
	void reportIncludesTestStrategy() throws Exception {
		createSampleProject(tempDir);

		String report = BootProjectAnalyzer.generateReport(tempDir);

		assertThat(report).contains("## Recommended Test Strategy");
		assertThat(report).contains("@WebMvcTest");
		assertThat(report).contains("MockMvc");
	}

	@Test
	void reportIncludesDependencyInfo() throws Exception {
		createSampleProject(tempDir);

		String report = BootProjectAnalyzer.generateReport(tempDir);

		assertThat(report).contains("## Dependencies & Versions");
		assertThat(report).contains("spring-boot-starter-web");
	}

	@Test
	void handlesProjectWithNoSrcDir() throws Exception {
		// Just a pom.xml, no src/
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());

		String report = BootProjectAnalyzer.generateReport(tempDir);
		assertThat(report).contains("# Project Analysis");
		// Should not throw
	}

	@Test
	void parseJavaFileDetectsRestController() {
		String source = """
				package com.example;
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.GetMapping;
				@RestController
				public class FooController {
				    @GetMapping("/foo")
				    public String foo() { return "foo"; }
				}
				""";

		var info = BootProjectAnalyzer.parseJavaFile(source, "src/main/java/com/example/FooController.java");

		assertThat(info.className()).isEqualTo("FooController");
		assertThat(info.hasAnnotation("RestController")).isTrue();
		assertThat(info.methods()).contains("foo");
	}

	@Test
	void parseJavaFileDetectsRecord() {
		String source = """
				package com.example;
				public record Greeting(String id, String content) {}
				""";

		var info = BootProjectAnalyzer.parseJavaFile(source, "src/main/java/com/example/Greeting.java");

		assertThat(info.className()).isEqualTo("Greeting");
		assertThat(info.type()).isEqualTo("record");
	}

	// --- helpers ---

	private void createSampleProject(Path root) throws Exception {
		Files.writeString(root.resolve("pom.xml"), minimalPom());

		Path mainJava = root.resolve("src/main/java/com/example/app");
		Files.createDirectories(mainJava);

		Files.writeString(mainJava.resolve("Application.java"), """
				package com.example.app;
				import org.springframework.boot.autoconfigure.SpringBootApplication;
				@SpringBootApplication
				public class Application {
				    public static void main(String[] args) {}
				}
				""");

		Files.writeString(mainJava.resolve("GreetingController.java"), """
				package com.example.app;
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.beans.factory.annotation.Autowired;
				@RestController
				public class GreetingController {
				    @Autowired private GreetingService service;
				    @GetMapping("/greeting")
				    public String greeting() { return service.greet(); }
				}
				""");

		Files.writeString(mainJava.resolve("GreetingService.java"), """
				package com.example.app;
				import org.springframework.stereotype.Service;
				@Service
				public class GreetingService {
				    public String greet() { return "Hello"; }
				}
				""");

		Path resources = root.resolve("src/main/resources");
		Files.createDirectories(resources);
		Files.writeString(resources.resolve("application.yml"), "# empty\n");
	}

	private String minimalPom() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <parent>
				    <groupId>org.springframework.boot</groupId>
				    <artifactId>spring-boot-starter-parent</artifactId>
				    <version>4.0.3</version>
				  </parent>
				  <properties>
				    <java.version>21</java.version>
				  </properties>
				  <dependencies>
				    <dependency>
				      <groupId>org.springframework.boot</groupId>
				      <artifactId>spring-boot-starter-web</artifactId>
				    </dependency>
				    <dependency>
				      <groupId>org.springframework.boot</groupId>
				      <artifactId>spring-boot-starter-test</artifactId>
				      <scope>test</scope>
				    </dependency>
				  </dependencies>
				</project>
				""";
	}

}
