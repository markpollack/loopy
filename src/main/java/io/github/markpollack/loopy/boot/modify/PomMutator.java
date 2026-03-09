package io.github.markpollack.loopy.boot.modify;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic POM mutation tool wrapping {@code MavenXpp3Reader/Writer}.
 *
 * <p>
 * All operations read the current model, apply a mutation, and write back. The POM is
 * never modified via string replacement or DOM manipulation.
 * </p>
 */
public class PomMutator {

	private static final Logger logger = LoggerFactory.getLogger(PomMutator.class);

	private final Path pomFile;

	public PomMutator(Path pomFile) {
		this.pomFile = pomFile;
	}

	/**
	 * Set {@code <java.version>} property and update compiler plugin source/target if
	 * present.
	 * @return description of what changed
	 */
	public String setJavaVersion(String version) throws IOException, XmlPullParserException {
		Model model = read();
		boolean changed = false;

		if (model.getProperties() != null) {
			String existing = model.getProperties().getProperty("java.version");
			if (!version.equals(existing)) {
				model.getProperties().setProperty("java.version", version);
				changed = true;
				logger.info("Set java.version={}", version);
			}
		}

		// Also update maven.compiler.source/target if present
		if (model.getProperties() != null) {
			if (model.getProperties().containsKey("maven.compiler.source")) {
				model.getProperties().setProperty("maven.compiler.source", version);
				changed = true;
			}
			if (model.getProperties().containsKey("maven.compiler.target")) {
				model.getProperties().setProperty("maven.compiler.target", version);
				changed = true;
			}
			if (model.getProperties().containsKey("maven.compiler.release")) {
				model.getProperties().setProperty("maven.compiler.release", version);
				changed = true;
			}
		}

		if (changed) {
			write(model);
			return "Set java.version=" + version;
		}
		return "java.version already set to " + version + " (no change)";
	}

	/**
	 * Add a dependency. Skips if already present (matching groupId + artifactId).
	 * @return description of what changed
	 */
	public String addDependency(String groupId, String artifactId, @Nullable String version, @Nullable String scope)
			throws IOException, XmlPullParserException {
		Model model = read();

		boolean alreadyPresent = model.getDependencies()
			.stream()
			.anyMatch(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()));
		if (alreadyPresent) {
			return groupId + ":" + artifactId + " already present (no change)";
		}

		Dependency dep = new Dependency();
		dep.setGroupId(groupId);
		dep.setArtifactId(artifactId);
		if (version != null) {
			dep.setVersion(version);
		}
		if (scope != null) {
			dep.setScope(scope);
		}
		model.addDependency(dep);

		write(model);
		return "Added dependency: " + groupId + ":" + artifactId + (version != null ? ":" + version : "");
	}

	/**
	 * Add a plugin to the build/plugins section. Skips if already present.
	 * @return description of what changed
	 */
	public String addPlugin(String groupId, String artifactId, @Nullable String version)
			throws IOException, XmlPullParserException {
		Model model = read();

		if (model.getBuild() == null) {
			model.setBuild(new org.apache.maven.model.Build());
		}

		boolean alreadyPresent = model.getBuild().getPlugins() != null && model.getBuild()
			.getPlugins()
			.stream()
			.anyMatch(p -> groupId.equals(p.getGroupId()) && artifactId.equals(p.getArtifactId()));
		if (alreadyPresent) {
			return groupId + ":" + artifactId + " already present in build/plugins (no change)";
		}

		Plugin plugin = new Plugin();
		plugin.setGroupId(groupId);
		plugin.setArtifactId(artifactId);
		if (version != null) {
			plugin.setVersion(version);
		}
		model.getBuild().addPlugin(plugin);

		write(model);
		return "Added plugin: " + groupId + ":" + artifactId + (version != null ? ":" + version : "");
	}

	/**
	 * Remove null/empty fields across all POM sections (port of DaShaun's cleanPom
	 * operation).
	 * @return description of what changed
	 */
	public String cleanPom() throws IOException, XmlPullParserException {
		Model model = read();
		int count = cleanModel(model);
		write(model);
		return "Cleaned pom.xml — removed " + count + " empty field(s)";
	}

	/**
	 * Remove a dependency by groupId + artifactId.
	 * @return description of what changed
	 */
	public String removeDependency(String groupId, String artifactId) throws IOException, XmlPullParserException {
		Model model = read();
		int before = model.getDependencies().size();
		model.getDependencies().removeIf(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()));
		int removed = before - model.getDependencies().size();
		if (removed > 0) {
			write(model);
			return "Removed dependency: " + groupId + ":" + artifactId;
		}
		return groupId + ":" + artifactId + " not found in dependencies (no change)";
	}

	// --- Internal ---

	private Model read() throws IOException, XmlPullParserException {
		try (FileReader fr = new FileReader(pomFile.toFile())) {
			return new MavenXpp3Reader().read(fr);
		}
	}

	private void write(Model model) throws IOException {
		try (FileWriter fw = new FileWriter(pomFile.toFile())) {
			new MavenXpp3Writer().write(fw, model);
		}
	}

	private static int cleanModel(Model model) {
		AtomicInteger count = new AtomicInteger();

		// Clean dependencies
		if (model.getDependencies() != null) {
			model.getDependencies().removeIf(dep -> {
				boolean isEmpty = (dep.getGroupId() == null || dep.getGroupId().isBlank())
						&& (dep.getArtifactId() == null || dep.getArtifactId().isBlank());
				if (isEmpty) {
					count.getAndIncrement();
					return true;
				}
				if (dep.getClassifier() != null && dep.getClassifier().isBlank())
					dep.setClassifier(null);
				if (dep.getType() != null && dep.getType().isBlank())
					dep.setType(null);
				if (dep.getScope() != null && dep.getScope().isBlank())
					dep.setScope(null);
				return false;
			});
		}

		// Clean empty properties
		if (model.getProperties() != null) {
			List<Object> keysToRemove = new ArrayList<>();
			model.getProperties().forEach((key, value) -> {
				if (value == null || value.toString().isBlank()) {
					keysToRemove.add(key);
					count.getAndIncrement();
				}
			});
			keysToRemove.forEach(model.getProperties()::remove);
		}

		// Clean empty top-level fields
		if (model.getName() != null && model.getName().isBlank()) {
			model.setName(null);
			count.getAndIncrement();
		}
		if (model.getDescription() != null && model.getDescription().isBlank()) {
			model.setDescription(null);
			count.getAndIncrement();
		}
		if (model.getUrl() != null && model.getUrl().isBlank()) {
			model.setUrl(null);
			count.getAndIncrement();
		}

		return count.get();
	}

}
