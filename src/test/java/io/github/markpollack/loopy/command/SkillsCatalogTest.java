package io.github.markpollack.loopy.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsCatalogTest {

	@Test
	void loadCatalogFromClasspath() {
		var catalog = SkillsCatalog.load();
		assertThat(catalog.all()).isNotEmpty();
	}

	@Test
	void searchByName() {
		var catalog = SkillsCatalog.load();
		var results = catalog.search("spring-boot");
		assertThat(results).isNotEmpty();
		assertThat(results).anyMatch(e -> e.name.equals("spring-boot"));
	}

	@Test
	void searchByTag() {
		var catalog = SkillsCatalog.load();
		var results = catalog.search("debugging");
		assertThat(results).isNotEmpty();
		assertThat(results).anyMatch(e -> e.name.equals("jdb-agentic-debugger"));
	}

	@Test
	void searchByAuthor() {
		var catalog = SkillsCatalog.load();
		var results = catalog.search("Anthropic");
		assertThat(results).hasSizeGreaterThanOrEqualTo(3);
	}

	@Test
	void searchNoResults() {
		var catalog = SkillsCatalog.load();
		var results = catalog.search("nonexistent-skill-xyz");
		assertThat(results).isEmpty();
	}

	@Test
	void searchBlankReturnsAll() {
		var catalog = SkillsCatalog.load();
		var results = catalog.search("");
		assertThat(results).isEqualTo(catalog.all());
	}

	@Test
	void findByNameExact() {
		var catalog = SkillsCatalog.load();
		var result = catalog.findByName("pdf");
		assertThat(result).isPresent();
		assertThat(result.get().author).isEqualTo("Anthropic");
	}

	@Test
	void findByNameCaseInsensitive() {
		var catalog = SkillsCatalog.load();
		var result = catalog.findByName("PDF");
		assertThat(result).isPresent();
	}

	@Test
	void findByNameNotFound() {
		var catalog = SkillsCatalog.load();
		var result = catalog.findByName("nonexistent");
		assertThat(result).isEmpty();
	}

	@Test
	void catalogEntriesHaveRequiredFields() {
		var catalog = SkillsCatalog.load();
		for (var entry : catalog.all()) {
			assertThat(entry.name).isNotBlank();
			assertThat(entry.description).isNotBlank();
			assertThat(entry.author).isNotBlank();
			assertThat(entry.tags).isNotNull().isNotEmpty();
			assertThat(entry.source).isNotNull();
			assertThat(entry.source.type).isEqualTo("github");
			assertThat(entry.source.repo).isNotBlank();
		}
	}

}
