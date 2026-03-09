package io.github.markpollack.loopy.boot.modify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorsTest {

	@TempDir
	Path tempDir;

	// --- JvmVersionResolver ---

	@Test
	void resolvesJavaVersionFromProperty() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithProperty("java.version", "21"));
		assertThat(JvmVersionResolver.resolve(tempDir.resolve("pom.xml"))).isEqualTo("21");
	}

	@Test
	void resolvesCompilerReleaseIfNoJavaVersion() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithProperty("maven.compiler.release", "17"));
		assertThat(JvmVersionResolver.resolve(tempDir.resolve("pom.xml"))).isEqualTo("17");
	}

	@Test
	void returnsNullWhenNoVersionProperty() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		assertThat(JvmVersionResolver.resolve(tempDir.resolve("pom.xml"))).isNull();
	}

	// --- SqlDialectDetector ---

	@Test
	void detectsPostgres() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithDep("org.postgresql", "postgresql"));
		assertThat(SqlDialectDetector.detect(tempDir.resolve("pom.xml"))).isEqualTo("postgres");
	}

	@Test
	void detectsMysql() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithDep("com.mysql", "mysql-connector-j"));
		assertThat(SqlDialectDetector.detect(tempDir.resolve("pom.xml"))).isEqualTo("mysql");
	}

	@Test
	void detectsH2() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithDep("com.h2database", "h2"));
		assertThat(SqlDialectDetector.detect(tempDir.resolve("pom.xml"))).isEqualTo("h2");
	}

	@Test
	void returnsNullWhenNoSqlDep() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		assertThat(SqlDialectDetector.detect(tempDir.resolve("pom.xml"))).isNull();
	}

	// --- NativeDetector ---

	@Test
	void detectsNativeMavenPlugin() throws Exception {
		String pom = """
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
				        <version>0.10.3</version>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""";
		Files.writeString(tempDir.resolve("pom.xml"), pom);
		assertThat(NativeDetector.isNative(tempDir.resolve("pom.xml"))).isTrue();
	}

	@Test
	void returnsFalseWhenNoNativePlugin() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), minimalPom());
		assertThat(NativeDetector.isNative(tempDir.resolve("pom.xml"))).isFalse();
	}

	// --- BootModifyCommand deterministic operations ---

	@Test
	void bootModifySetJavaVersionDeterministic() throws Exception {
		Files.writeString(tempDir.resolve("pom.xml"), pomWithProperty("java.version", "17"));
		var command = new io.github.markpollack.loopy.boot.BootModifyCommand(null);
		var ctx = new io.github.markpollack.loopy.command.CommandContext(tempDir, () -> {
		});

		String result = command.execute("set java version 21", ctx);

		assertThat(result).contains("21");
		String pom = Files.readString(tempDir.resolve("pom.xml"));
		assertThat(pom).contains("<java.version>21</java.version>");
	}

	@Test
	void bootModifyCleanPomDeterministic() throws Exception {
		String pom = """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <name>   </name>
				  <properties>
				    <java.version>21</java.version>
				    <empty.prop></empty.prop>
				  </properties>
				  <dependencies/>
				</project>
				""";
		Files.writeString(tempDir.resolve("pom.xml"), pom);
		var command = new io.github.markpollack.loopy.boot.BootModifyCommand(null);
		var ctx = new io.github.markpollack.loopy.command.CommandContext(tempDir, () -> {
		});

		String result = command.execute("clean pom", ctx);

		assertThat(result).containsIgnoringCase("cleaned");
	}

	// --- helpers ---

	private String pomWithProperty(String key, String value) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <properties>
				    <%s>%s</%s>
				  </properties>
				  <dependencies/>
				</project>
				""".formatted(key, value, key);
	}

	private String pomWithDep(String groupId, String artifactId) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<project>
				  <modelVersion>4.0.0</modelVersion>
				  <groupId>com.example</groupId>
				  <artifactId>app</artifactId>
				  <version>0.0.1-SNAPSHOT</version>
				  <dependencies>
				    <dependency>
				      <groupId>%s</groupId>
				      <artifactId>%s</artifactId>
				    </dependency>
				  </dependencies>
				</project>
				""".formatted(groupId, artifactId);
	}

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

}
