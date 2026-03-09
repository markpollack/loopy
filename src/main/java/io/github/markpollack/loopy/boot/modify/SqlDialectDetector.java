package io.github.markpollack.loopy.boot.modify;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jspecify.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Detects the SQL dialect from POM dependencies.
 *
 * <p>
 * Returns one of: {@code postgres}, {@code mysql}, {@code mariadb}, {@code oracle},
 * {@code sqlserver}, {@code h2}, or {@code null} (no SQL dependency found).
 * </p>
 */
public class SqlDialectDetector {

	/**
	 * Detect the SQL dialect from dependencies in the given pom.xml.
	 */
	public static @Nullable String detect(Path pomFile) {
		try (FileReader fr = new FileReader(pomFile.toFile())) {
			Model model = new MavenXpp3Reader().read(fr);
			return detectFromModel(model);
		}
		catch (IOException | XmlPullParserException ex) {
			return null;
		}
	}

	/**
	 * Detect from a pre-parsed Maven model.
	 */
	public static @Nullable String detectFromModel(Model model) {
		if (model.getDependencies() == null) {
			return null;
		}
		for (var dep : model.getDependencies()) {
			String artifactId = dep.getArtifactId() != null ? dep.getArtifactId().toLowerCase() : "";
			String groupId = dep.getGroupId() != null ? dep.getGroupId().toLowerCase() : "";

			if (artifactId.contains("postgresql") || groupId.contains("postgresql")) {
				return "postgres";
			}
			if (artifactId.contains("mysql") || groupId.contains("mysql")) {
				return "mysql";
			}
			if (artifactId.contains("mariadb") || groupId.contains("mariadb")) {
				return "mariadb";
			}
			if (artifactId.contains("oracle") || groupId.contains("oracle")) {
				return "oracle";
			}
			if (artifactId.contains("mssql") || artifactId.contains("sqlserver") || groupId.contains("sqlserver")) {
				return "sqlserver";
			}
			if (artifactId.contains("h2")) {
				return "h2";
			}
			if (artifactId.contains("hsqldb")) {
				return "hsqldb";
			}
		}
		return null;
	}

}
