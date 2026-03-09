package io.github.markpollack.loopy.boot;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User preferences for {@code /boot-new} scaffolding, persisted to
 * {@code ~/.config/loopy/boot/preferences.yml}.
 *
 * <p>
 * After a successful {@code /boot-new} invocation, the {@code groupId} and
 * {@code javaVersion} are saved so they don't need to be re-supplied on the next run.
 * </p>
 */
public record BootPreferences(@Nullable String javaVersion, @Nullable String groupId, List<String> alwaysAdd,
		@Nullable String preferDatabase) {

	/** Default preferences applied when no preferences file exists. */
	public static BootPreferences defaults() {
		return new BootPreferences("21", null, List.of(), null);
	}

	/** The canonical path of the preferences file. */
	public static Path prefsPath() {
		return Path.of(System.getProperty("user.home"), ".config", "loopy", "boot", "preferences.yml");
	}

	/**
	 * Load preferences from the default path, returning {@link #defaults()} if missing.
	 */
	public static BootPreferences load() {
		return load(prefsPath());
	}

	/** Load preferences from {@code path}, returning {@link #defaults()} if missing. */
	@SuppressWarnings("unchecked")
	public static BootPreferences load(Path path) {
		if (!Files.exists(path)) {
			return defaults();
		}
		try {
			String yaml = Files.readString(path, StandardCharsets.UTF_8);
			Map<String, Object> map = new Yaml().load(yaml);
			if (map == null) {
				return defaults();
			}
			String javaVersion = (String) map.get("javaVersion");
			String groupId = (String) map.get("groupId");
			List<String> alwaysAdd = (List<String>) map.getOrDefault("alwaysAdd", List.of());
			String preferDatabase = (String) map.get("preferDatabase");
			return new BootPreferences(javaVersion, groupId, alwaysAdd != null ? alwaysAdd : List.of(), preferDatabase);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to load preferences from " + path, ex);
		}
	}

	/** Save preferences to the default path, creating parent directories as needed. */
	public void save() {
		save(prefsPath());
	}

	/** Save preferences to {@code path}, creating parent directories as needed. */
	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			DumperOptions opts = new DumperOptions();
			opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			Yaml yaml = new Yaml(opts);
			Map<String, Object> map = new LinkedHashMap<>();
			if (javaVersion != null) {
				map.put("javaVersion", javaVersion);
			}
			if (groupId != null) {
				map.put("groupId", groupId);
			}
			if (!alwaysAdd.isEmpty()) {
				map.put("alwaysAdd", alwaysAdd);
			}
			if (preferDatabase != null) {
				map.put("preferDatabase", preferDatabase);
			}
			Files.writeString(path, yaml.dump(map), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to save preferences to " + path, ex);
		}
	}

	/**
	 * Return a new {@link BootBrief} with fields filled in from these preferences
	 * wherever the brief has no value.
	 */
	public BootBrief applyToBootBrief(BootBrief brief) {
		String jv = brief.javaVersion() != null ? brief.javaVersion() : (javaVersion != null ? javaVersion : "21");
		String gid = brief.groupId() != null ? brief.groupId() : groupId;
		return new BootBrief(brief.name(), gid, brief.artifactId(), brief.packageName(), brief.template(), jv);
	}

}
