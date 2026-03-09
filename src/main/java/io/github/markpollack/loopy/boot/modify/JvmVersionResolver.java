package io.github.markpollack.loopy.boot.modify;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jspecify.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves the effective Java version from a Maven POM.
 *
 * <p>
 * Checks, in order:
 * <ol>
 * <li>{@code java.version} property</li>
 * <li>{@code maven.compiler.release} property</li>
 * <li>{@code maven.compiler.source} property</li>
 * </ol>
 * Returns {@code null} if no Java version property is found.
 * </p>
 */
public class JvmVersionResolver {

	/**
	 * Resolve the Java version from the pom.xml at the given path.
	 */
	public static @Nullable String resolve(Path pomFile) {
		try (FileReader fr = new FileReader(pomFile.toFile())) {
			Model model = new MavenXpp3Reader().read(fr);
			return resolveFromModel(model);
		}
		catch (IOException | XmlPullParserException ex) {
			return null;
		}
	}

	/**
	 * Resolve from a pre-parsed Maven model.
	 */
	public static @Nullable String resolveFromModel(Model model) {
		if (model.getProperties() == null) {
			return null;
		}
		String javaVersion = model.getProperties().getProperty("java.version");
		if (javaVersion != null && !javaVersion.isBlank()) {
			return javaVersion.trim();
		}
		String release = model.getProperties().getProperty("maven.compiler.release");
		if (release != null && !release.isBlank()) {
			return release.trim();
		}
		String source = model.getProperties().getProperty("maven.compiler.source");
		if (source != null && !source.isBlank()) {
			return source.trim();
		}
		return null;
	}

}
