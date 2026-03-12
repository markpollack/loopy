package io.github.markpollack.loopy.boot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BootNewTool} — verifies the tool creates a valid project
 * structure when called directly with structured parameters (as the agent would call it
 * after extracting them from natural language).
 */
class BootNewToolTest {

	@TempDir
	Path tempDir;

	BootNewTool tool;

	@BeforeEach
	void setUp() {
		// null ChatModel — skips LLM domain-fill, tests deterministic scaffold only
		tool = new BootNewTool(tempDir, null);
	}

	// --- happy path ---

	@Test
	void scaffoldsMinimalTemplate() throws Exception {
		String result = tool.bootNew("my-app", "com.example", "spring-boot-minimal", null);

		assertThat(result).isNotBlank();
		Path project = tempDir.resolve("my-app");
		assertThat(project.resolve("pom.xml")).isRegularFile();
		assertThat(project.resolve("src/main/java/com/example/myapp/Application.java")).isRegularFile();
	}

	@Test
	void scaffoldsRestTemplate() throws Exception {
		tool.bootNew("orders-api", "com.acme", "spring-boot-rest", null);

		Path project = tempDir.resolve("orders-api");
		assertThat(project.resolve("src/main/java/com/acme/ordersapi/greeting/GreetingController.java"))
			.isRegularFile();
	}

	@Test
	void scaffoldsJpaTemplate() throws Exception {
		tool.bootNew("catalog-service", "com.corp", "spring-boot-jpa", null);

		Path project = tempDir.resolve("catalog-service");
		assertThat(project.resolve("pom.xml")).isRegularFile();
		String pom = Files.readString(project.resolve("pom.xml"));
		assertThat(pom).contains("spring-boot-starter-data-jpa");
	}

	@Test
	void pomHasCorrectGav() throws Exception {
		tool.bootNew("widget-service", "com.corp", "spring-boot-minimal", null);

		String pom = Files.readString(tempDir.resolve("widget-service/pom.xml"));
		assertThat(pom).contains("<groupId>com.corp</groupId>").contains("<artifactId>widget-service</artifactId>");
	}

	@Test
	void javaVersionAppliedToPom() throws Exception {
		tool.bootNew("my-app", "com.example", "spring-boot-minimal", "17");

		String pom = Files.readString(tempDir.resolve("my-app/pom.xml"));
		assertThat(pom).contains("<java.version>17</java.version>");
	}

	@Test
	void packageIsRenamedFromTemplate() throws Exception {
		tool.bootNew("invoice-svc", "io.payments", "spring-boot-minimal", null);

		Path app = tempDir.resolve("invoice-svc/src/main/java/io/payments/invoicesvc/Application.java");
		assertThat(app).isRegularFile();
		assertThat(Files.readString(app)).contains("package io.payments.invoicesvc");
	}

	@Test
	void projectCreatedAsSubdirectory() throws Exception {
		tool.bootNew("orders-api", "com.acme", "spring-boot-minimal", null);

		assertThat(tempDir.resolve("orders-api")).isDirectory();
		// working directory itself is unchanged
		assertThat(tempDir.resolve("pom.xml")).doesNotExist();
	}

	// --- error cases ---

	@Test
	void missingNameReturnsError() {
		String result = tool.bootNew(null, "com.example", "spring-boot-minimal", null);
		assertThat(result).containsIgnoringCase("required").doesNotContain("Exception");
	}

	@Test
	void blankNameReturnsError() {
		String result = tool.bootNew("  ", "com.example", "spring-boot-minimal", null);
		assertThat(result).containsIgnoringCase("required");
	}

	@Test
	void unknownTemplateReturnsError() {
		String result = tool.bootNew("my-app", "com.example", "spring-boot-unknown", null);
		assertThat(result).containsIgnoringCase("unknown template");
	}

	@Test
	void missingGroupFallsBackOrReturnsError() {
		// No group provided and no saved preferences in CI — either succeeds (prefs on
		// disk)
		// or returns a clear message; must not throw an exception
		String result = tool.bootNew("my-app", null, "spring-boot-minimal", null);
		assertThat(result).isNotBlank().doesNotContain("Exception");
	}

}
