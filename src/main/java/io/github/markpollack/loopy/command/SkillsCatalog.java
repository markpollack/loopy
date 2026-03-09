package io.github.markpollack.loopy.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Curated catalog of known skills, baked into the JAR at {@code skills-catalog.json}.
 * Supports search by name, tag, and description.
 */
public class SkillsCatalog {

	private static final String CATALOG_RESOURCE = "/skills-catalog.json";

	private final List<CatalogEntry> entries;

	private SkillsCatalog(List<CatalogEntry> entries) {
		this.entries = entries;
	}

	/**
	 * Load the catalog from the classpath resource.
	 */
	public static SkillsCatalog load() {
		try (InputStream is = SkillsCatalog.class.getResourceAsStream(CATALOG_RESOURCE)) {
			if (is == null) {
				return new SkillsCatalog(Collections.emptyList());
			}
			var mapper = new ObjectMapper();
			var catalog = mapper.readValue(is, CatalogFile.class);
			return new SkillsCatalog(catalog.skills != null ? catalog.skills : Collections.emptyList());
		}
		catch (IOException ex) {
			return new SkillsCatalog(Collections.emptyList());
		}
	}

	/**
	 * Search for skills matching a query. Matches against name, description, tags, and
	 * author (case-insensitive substring).
	 */
	public List<CatalogEntry> search(String query) {
		if (query == null || query.isBlank()) {
			return entries;
		}
		String lowerQuery = query.toLowerCase();
		return entries.stream().filter(e -> matches(e, lowerQuery)).toList();
	}

	/**
	 * Look up a specific skill by exact name.
	 */
	public Optional<CatalogEntry> findByName(String name) {
		return entries.stream().filter(e -> e.name.equalsIgnoreCase(name)).findFirst();
	}

	/**
	 * All entries in the catalog.
	 */
	public List<CatalogEntry> all() {
		return Collections.unmodifiableList(entries);
	}

	/**
	 * Check if a skill is installed locally.
	 */
	public static boolean isInstalled(String skillName) {
		Path globalDir = Path.of(System.getProperty("user.home"), ".claude", "skills", skillName);
		return Files.isDirectory(globalDir) && Files.exists(globalDir.resolve("SKILL.md"));
	}

	private boolean matches(CatalogEntry entry, String query) {
		if (entry.name != null && entry.name.toLowerCase().contains(query)) {
			return true;
		}
		if (entry.description != null && entry.description.toLowerCase().contains(query)) {
			return true;
		}
		if (entry.author != null && entry.author.toLowerCase().contains(query)) {
			return true;
		}
		if (entry.tags != null) {
			for (String tag : entry.tags) {
				if (tag.toLowerCase().contains(query)) {
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

		public List<CatalogEntry> skills;

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CatalogEntry {

		public String name;

		public String description;

		public String author;

		public List<String> tags;

		public CatalogSource source;

		public String skillsjars;

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CatalogSource {

		public String type;

		public String repo;

		public String path;

		public String branch;

	}

}
