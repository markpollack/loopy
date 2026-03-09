package io.github.markpollack.loopy.boot.modify;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Detects whether a Maven project uses GraalVM native image compilation.
 *
 * <p>
 * Checks for the presence of {@code native-maven-plugin} or
 * {@code spring-boot-starter-parent} with native profile configuration in the POM. When
 * {@code native-maven-plugin} is present, GraalVM should be the JVM.
 * </p>
 */
public class NativeDetector {

	/**
	 * Returns {@code true} if {@code native-maven-plugin} is present in the POM.
	 */
	public static boolean isNative(Path pomFile) {
		try (FileReader fr = new FileReader(pomFile.toFile())) {
			Model model = new MavenXpp3Reader().read(fr);
			return isNativeFromModel(model);
		}
		catch (IOException | XmlPullParserException ex) {
			return false;
		}
	}

	/**
	 * Detect from a pre-parsed Maven model.
	 */
	public static boolean isNativeFromModel(Model model) {
		if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
			boolean inPlugins = model.getBuild()
				.getPlugins()
				.stream()
				.anyMatch(p -> "native-maven-plugin".equals(p.getArtifactId()));
			if (inPlugins) {
				return true;
			}
		}
		// Also check profiles
		if (model.getProfiles() != null) {
			for (var profile : model.getProfiles()) {
				if (profile.getBuild() != null && profile.getBuild().getPlugins() != null) {
					boolean inProfile = profile.getBuild()
						.getPlugins()
						.stream()
						.anyMatch(p -> "native-maven-plugin".equals(p.getArtifactId()));
					if (inProfile) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
