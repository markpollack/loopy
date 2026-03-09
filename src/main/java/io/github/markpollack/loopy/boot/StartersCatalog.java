package io.github.markpollack.loopy.boot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Curated catalog of known Agent Starters, baked into the JAR at
 * {@code starters-catalog.json}. Also supports classpath scanning for starters
 * contributed via {@code META-INF/agent-starters/*.yaml}.
 *
 * <p>
 * This catalog is SEPARATE from {@link io.github.markpollack.loopy.command.SkillsCatalog}
 * — different file, different command, different mental model. Agent Starters are full
 * packages (skill + SAE + tools + auto-config); skills are knowledge-only artifacts.
 * </p>
 */
public class StartersCatalog {

	private static final Logger logger = LoggerFactory.getLogger(StartersCatalog.class);

	private static final String CATALOG_RESOURCE = "/starters-catalog.json";

	private static final String CLASSPATH_STARTERS_PATTERN = "classpath*:META-INF/agent-starters/*.yaml";

	private final List<StarterEntry> entries;

	private StartersCatalog(List<StarterEntry> entries) {
		this.entries = entries;
	}

	/**
	 * Load the baked-in catalog from the classpath resource, merged with any
	 * {@code META-INF/agent-starters/*.yaml} entries found on the classpath.
	 */
	public static StartersCatalog load() {
		List<StarterEntry> result = new ArrayList<>();

		// Load from baked-in JSON catalog
		try (InputStream is = StartersCatalog.class.getResourceAsStream(CATALOG_RESOURCE)) {
			if (is != null) {
				var mapper = new ObjectMapper();
				var catalog = mapper.readValue(is, CatalogFile.class);
				if (catalog.starters != null) {
					result.addAll(catalog.starters);
				}
			}
		}
		catch (IOException ex) {
			logger.debug("Could not load starters-catalog.json: {}", ex.getMessage());
		}

		// Merge classpath-scanned entries (from Agent Starter JARs)
		result.addAll(classpathScan());

		return new StartersCatalog(result);
	}

	/**
	 * Scan {@code META-INF/agent-starters/*.yaml} on the classpath for Agent Starters
	 * contributed by JAR dependencies.
	 */
	static List<StarterEntry> classpathScan() {
		List<StarterEntry> found = new ArrayList<>();
		try {
			var resolver = new PathMatchingResourcePatternResolver();
			var resources = resolver.getResources(CLASSPATH_STARTERS_PATTERN);
			var yaml = new Yaml();
			for (var resource : resources) {
				try (InputStream is = resource.getInputStream()) {
					Map<String, Object> map = yaml.load(is);
					if (map != null) {
						found.add(fromMap(map));
					}
				}
				catch (Exception ex) {
					logger.debug("Skipping unparseable starter metadata at {}: {}", resource.getDescription(),
							ex.getMessage());
				}
			}
		}
		catch (IOException ex) {
			logger.debug("Classpath scan for agent-starters found nothing: {}", ex.getMessage());
		}
		return found;
	}

	/**
	 * Search for starters matching a query. Matches against name, description, and
	 * triggers (case-insensitive substring).
	 */
	public List<StarterEntry> search(String query) {
		if (query == null || query.isBlank()) {
			return Collections.unmodifiableList(entries);
		}
		String lowerQuery = query.toLowerCase();
		return entries.stream().filter(e -> matches(e, lowerQuery)).toList();
	}

	/**
	 * Find a specific starter by exact name match.
	 */
	public Optional<StarterEntry> findByName(String name) {
		return entries.stream().filter(e -> name.equalsIgnoreCase(e.name)).findFirst();
	}

	/**
	 * All entries in the catalog.
	 */
	public List<StarterEntry> all() {
		return Collections.unmodifiableList(entries);
	}

	/**
	 * Find starters whose triggers match any of the given dependency artifactIds.
	 */
	public List<StarterEntry> findByTriggers(List<String> artifactIds) {
		return entries.stream()
			.filter(e -> e.triggers != null && e.triggers.stream().anyMatch(artifactIds::contains))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private static StarterEntry fromMap(Map<String, Object> map) {
		var entry = new StarterEntry();
		entry.name = (String) map.get("name");
		entry.description = (String) map.get("description");
		entry.version = (String) map.get("version");
		entry.mavenCoordinates = (String) map.get("mavenCoordinates");
		entry.source = (String) map.get("source");
		Object triggers = map.get("triggers");
		if (triggers instanceof List<?> list) {
			entry.triggers = (List<String>) list;
		}
		Object commands = map.get("commands");
		if (commands instanceof List<?> list) {
			entry.commands = (List<String>) list;
		}
		return entry;
	}

	private boolean matches(StarterEntry entry, String query) {
		if (entry.name != null && entry.name.toLowerCase().contains(query)) {
			return true;
		}
		if (entry.description != null && entry.description.toLowerCase().contains(query)) {
			return true;
		}
		if (entry.triggers != null) {
			for (String trigger : entry.triggers) {
				if (trigger.toLowerCase().contains(query)) {
					return true;
				}
			}
		}
		return false;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class CatalogFile {

		public String version;

		public String updated;

		public List<StarterEntry> starters;

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class StarterEntry {

		public @Nullable String name;

		public @Nullable String description;

		public @Nullable String version;

		/** Dependency artifactIds that trigger a suggestion for this starter. */
		public @Nullable List<String> triggers;

		/** Example commands this starter enables. */
		public @Nullable List<String> commands;

		/** Maven coordinates: {@code groupId:artifactId:version}. */
		public @Nullable String mavenCoordinates;

		/** Human-readable source label (e.g., "Maven Central", "GitHub"). */
		public @Nullable String source;

	}

}
