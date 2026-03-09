package io.github.markpollack.loopy.boot;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BootPreferencesTest {

	@TempDir
	Path tempDir;

	@Test
	void roundTripsAllFieldsViaYaml() throws Exception {
		Path prefsFile = tempDir.resolve("preferences.yml");

		BootPreferences prefs = new BootPreferences("21", "com.acme", List.of("spring-boot-starter-web"), "postgresql");
		prefs.save(prefsFile);

		BootPreferences loaded = BootPreferences.load(prefsFile);

		assertThat(loaded.javaVersion()).isEqualTo("21");
		assertThat(loaded.groupId()).isEqualTo("com.acme");
		assertThat(loaded.alwaysAdd()).containsExactly("spring-boot-starter-web");
		assertThat(loaded.preferDatabase()).isEqualTo("postgresql");
	}

	@Test
	void returnsDefaultsWhenFileAbsent() {
		Path missing = tempDir.resolve("nonexistent.yml");

		BootPreferences loaded = BootPreferences.load(missing);

		assertThat(loaded.javaVersion()).isEqualTo("21");
		assertThat(loaded.groupId()).isNull();
		assertThat(loaded.alwaysAdd()).isEmpty();
		assertThat(loaded.preferDatabase()).isNull();
	}

	@Test
	void createsParentDirectoriesOnSave() throws Exception {
		Path nested = tempDir.resolve("a/b/c/preferences.yml");

		new BootPreferences("17", "org.example", List.of(), null).save(nested);

		assertThat(nested).isRegularFile();
		BootPreferences loaded = BootPreferences.load(nested);
		assertThat(loaded.javaVersion()).isEqualTo("17");
		assertThat(loaded.groupId()).isEqualTo("org.example");
	}

	@Test
	void applyToBootBriefFillsMissingFields() {
		BootPreferences prefs = new BootPreferences("21", "com.acme", List.of(), null);

		// Brief has no groupId or javaVersion — should be filled from prefs
		BootBrief sparse = new BootBrief("my-app", null, "my-app", "com.acme.myapp", "spring-boot-minimal", null);
		BootBrief filled = prefs.applyToBootBrief(sparse);

		assertThat(filled.groupId()).isEqualTo("com.acme");
		assertThat(filled.javaVersion()).isEqualTo("21");
		assertThat(filled.name()).isEqualTo("my-app");
	}

	@Test
	void applyToBootBriefPrefersBriefValuesOverPrefs() {
		BootPreferences prefs = new BootPreferences("21", "com.acme", List.of(), null);

		// Brief has explicit values — prefs should NOT override
		BootBrief explicit = new BootBrief("my-app", "org.override", "my-app", "org.override.myapp",
				"spring-boot-minimal", "17");
		BootBrief result = prefs.applyToBootBrief(explicit);

		assertThat(result.groupId()).isEqualTo("org.override");
		assertThat(result.javaVersion()).isEqualTo("17");
	}

	@Test
	void omitsNullFieldsFromYaml() throws Exception {
		Path prefsFile = tempDir.resolve("preferences.yml");

		// Only javaVersion — no groupId, preferDatabase, alwaysAdd
		new BootPreferences("21", null, List.of(), null).save(prefsFile);

		String yaml = java.nio.file.Files.readString(prefsFile);
		assertThat(yaml).contains("javaVersion");
		assertThat(yaml).doesNotContain("groupId");
		assertThat(yaml).doesNotContain("preferDatabase");
		assertThat(yaml).doesNotContain("alwaysAdd");
	}

}
