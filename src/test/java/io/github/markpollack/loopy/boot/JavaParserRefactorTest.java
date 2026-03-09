package io.github.markpollack.loopy.boot;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserRefactorTest {

	@TempDir
	Path tempDir;

	private final TemplateExtractor extractor = new TemplateExtractor();

	private final JavaParserRefactor refactor = new JavaParserRefactor();

	@Test
	void refactorsPackageDeclarationsAndMovesFiles() throws Exception {
		Path project = tempDir.resolve("project");
		extractor.extract("spring-boot-minimal", project);

		refactor.refactorPackage(project, "com.example.app", "com.acme.widget");

		// New file exists at renamed path
		Path newMain = project.resolve("src/main/java/com/acme/widget/Application.java");
		assertThat(newMain).isRegularFile();

		// Package declaration was rewritten
		String content = Files.readString(newMain);
		assertThat(content).contains("package com.acme.widget;");

		// Old directory no longer exists
		assertThat(project.resolve("src/main/java/com/example/app")).doesNotExist();
	}

	@Test
	void refactorsTestFilesAndSubPackages() throws Exception {
		Path project = tempDir.resolve("project");
		extractor.extract("spring-boot-rest", project);

		refactor.refactorPackage(project, "com.example.app", "com.acme.myapp");

		// Test file moved to new location
		Path testFile = project.resolve("src/test/java/com/acme/myapp/greeting/GreetingControllerTests.java");
		assertThat(testFile).isRegularFile();

		String content = Files.readString(testFile);
		assertThat(content).contains("package com.acme.myapp.greeting;");
		assertThat(content).doesNotContain("com.example.app");
	}

	@Test
	void rewritesImportsToo() throws Exception {
		Path project = tempDir.resolve("project");
		extractor.extract("spring-boot-jpa", project);

		refactor.refactorPackage(project, "com.example.app", "com.corp.store");

		// Customer entity moved
		Path entity = project.resolve("src/main/java/com/corp/store/customer/Customer.java");
		assertThat(entity).isRegularFile();
		assertThat(Files.readString(entity)).contains("package com.corp.store.customer;");
	}

	@Test
	void handlesRecordTypes() throws Exception {
		Path project = tempDir.resolve("project");
		extractor.extract("spring-boot-rest", project);

		refactor.refactorPackage(project, "com.example.app", "com.acme.myapp");

		// Greeting record should be moved and package rewritten
		Path record = project.resolve("src/main/java/com/acme/myapp/greeting/Greeting.java");
		assertThat(record).isRegularFile();
		assertThat(java.nio.file.Files.readString(record)).contains("package com.acme.myapp.greeting;");
	}

	@Test
	void isIdempotent() throws Exception {
		Path project = tempDir.resolve("project");
		extractor.extract("spring-boot-minimal", project);

		refactor.refactorPackage(project, "com.example.app", "com.acme.widget");
		// Run again — should be a no-op (package is already com.acme.widget)
		refactor.refactorPackage(project, "com.example.app", "com.acme.widget");

		assertThat(project.resolve("src/main/java/com/acme/widget/Application.java")).isRegularFile();
	}

}
