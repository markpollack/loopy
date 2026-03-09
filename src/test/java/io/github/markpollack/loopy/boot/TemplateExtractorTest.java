package io.github.markpollack.loopy.boot;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateExtractorTest {

	@TempDir
	Path tempDir;

	private final TemplateExtractor extractor = new TemplateExtractor();

	@ParameterizedTest
	@ValueSource(strings = { "spring-boot-minimal", "spring-boot-rest", "spring-boot-jpa", "spring-ai-app" })
	void extractsAllFourTemplates(String templateName) throws Exception {
		Path target = tempDir.resolve(templateName);

		extractor.extract(templateName, target);

		assertThat(target).isDirectory();
		assertThat(target.resolve("pom.xml")).isRegularFile();
		assertThat(target.resolve("mvnw")).isRegularFile();
		assertThat(target.resolve("src/main/java/com/example/app/Application.java")).isRegularFile();
	}

	@Test
	void extractedPomContainsSpringBoot403() throws Exception {
		extractor.extract("spring-boot-minimal", tempDir.resolve("out"));

		String pom = Files.readString(tempDir.resolve("out/pom.xml"));
		assertThat(pom).contains("<version>4.0.3</version>");
		assertThat(pom).contains("<java.version>21</java.version>");
	}

	@Test
	void extractedRestTemplateContainsController() throws Exception {
		extractor.extract("spring-boot-rest", tempDir.resolve("out"));

		assertThat(tempDir.resolve("out/src/main/java/com/example/app/greeting/GreetingController.java"))
			.isRegularFile();
		assertThat(tempDir.resolve("out/src/test/java/com/example/app/greeting/GreetingControllerTests.java"))
			.isRegularFile();
	}

	@Test
	void extractedJpaTemplateContainsEntity() throws Exception {
		extractor.extract("spring-boot-jpa", tempDir.resolve("out"));

		assertThat(tempDir.resolve("out/src/main/java/com/example/app/customer/Customer.java")).isRegularFile();
		assertThat(tempDir.resolve("out/src/main/java/com/example/app/customer/CustomerRepository.java"))
			.isRegularFile();
	}

	@Test
	void unknownTemplateThrows() {
		assertThatThrownBy(() -> extractor.extract("does-not-exist", tempDir.resolve("out")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does-not-exist");
	}

	@Test
	void mvnwIsExecutableAfterExtraction() throws Exception {
		extractor.extract("spring-boot-minimal", tempDir.resolve("out"));

		Path mvnw = tempDir.resolve("out/mvnw");
		assertThat(mvnw).isRegularFile();
		assertThat(Files.isExecutable(mvnw)).isTrue();
	}

}
