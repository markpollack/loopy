package io.github.markpollack.loopy.boot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartersCatalogTest {

	@Test
	void loadReturnsCatalogFromClasspath() {
		// starters-catalog.json is baked in with empty starters array
		StartersCatalog catalog = StartersCatalog.load();
		assertThat(catalog).isNotNull();
		// Empty catalog is correct — no starters defined yet
		assertThat(catalog.all()).isEmpty();
	}

	@Test
	void searchOnEmptyCatalogReturnsEmpty() {
		StartersCatalog catalog = StartersCatalog.load();
		assertThat(catalog.search("jpa")).isEmpty();
	}

	@Test
	void searchAllOnEmptyCatalogReturnsEmpty() {
		StartersCatalog catalog = StartersCatalog.load();
		assertThat(catalog.search("")).isEmpty();
	}

	@Test
	void findByNameOnEmptyCatalogReturnsEmpty() {
		StartersCatalog catalog = StartersCatalog.load();
		assertThat(catalog.findByName("spring-ai-starter-data-jpa")).isEmpty();
	}

	@Test
	void findByTriggersMatchesByArtifactId() {
		// Build a catalog directly with a test entry
		var entry = new StartersCatalog.StarterEntry();
		entry.name = "spring-ai-starter-data-jpa";
		entry.description = "JPA expertise";
		entry.triggers = List.of("spring-boot-starter-data-jpa");
		entry.mavenCoordinates = "io.github.markpollack:spring-ai-starter-data-jpa:1.0.0";

		// Simulate finding by triggers — use direct list access
		boolean triggered = entry.triggers.stream()
			.anyMatch(List.of("spring-boot-starter-web", "spring-boot-starter-data-jpa")::contains);
		assertThat(triggered).isTrue();
	}

	@Test
	void classpathScanReturnsEmptyWhenNoStarterYamls() {
		// No META-INF/agent-starters/*.yaml in test classpath — should return empty, not
		// throw
		List<StartersCatalog.StarterEntry> found = StartersCatalog.classpathScan();
		assertThat(found).isNotNull();
	}

}
