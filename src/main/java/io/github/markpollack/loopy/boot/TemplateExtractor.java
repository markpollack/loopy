package io.github.markpollack.loopy.boot;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Extracts a bundled template from the classpath to a target directory on the filesystem.
 *
 * <p>
 * Templates live under {@code classpath:templates/{templateName}/} inside the Loopy JAR.
 * This class walks all resources under that prefix and copies them, preserving the
 * relative directory structure. The {@code mvnw} script is made executable after
 * extraction.
 * </p>
 */
public class TemplateExtractor {

	private static final Logger logger = LoggerFactory.getLogger(TemplateExtractor.class);

	private static final String TEMPLATES_ROOT = "templates/";

	/**
	 * Extract the named template to a target directory.
	 * @param templateName the template to extract (e.g., {@code spring-boot-rest})
	 * @param targetDir the directory to extract into — must not already exist
	 * @throws IllegalArgumentException if no resources found for the template
	 */
	public void extract(String templateName, Path targetDir) {
		String pattern = "classpath*:" + TEMPLATES_ROOT + templateName + "/**";
		String prefix = TEMPLATES_ROOT + templateName + "/";

		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources;
		try {
			resources = resolver.getResources(pattern);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to scan template resources: " + pattern, ex);
		}

		if (resources.length == 0) {
			throw new IllegalArgumentException("No template found for: " + templateName);
		}

		logger.info("Extracting template '{}' ({} resources) to {}", templateName, resources.length, targetDir);

		for (Resource resource : resources) {
			String path = resourcePath(resource, prefix);
			if (path == null || path.isBlank()) {
				continue; // skip directory entries
			}
			Path target = targetDir.resolve(path);
			copyResource(resource, target);
		}

		makeExecutable(targetDir.resolve("mvnw"));
		logger.info("Template '{}' extracted to {}", templateName, targetDir);
	}

	private String resourcePath(Resource resource, String prefix) {
		try {
			String urlString = resource.getURL().toString();
			int idx = urlString.indexOf(prefix);
			if (idx < 0) {
				return null;
			}
			return urlString.substring(idx + prefix.length());
		}
		catch (IOException ex) {
			logger.warn("Could not resolve URL for resource: {}", resource, ex);
			return null;
		}
	}

	private void copyResource(Resource resource, Path target) {
		try {
			if (!resource.isReadable()) {
				return; // skip directory entries
			}
			Files.createDirectories(target.getParent());
			try (InputStream in = resource.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				logger.debug("Copied: {}", target);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to copy resource to " + target, ex);
		}
	}

	private void makeExecutable(Path mvnw) {
		if (!Files.exists(mvnw)) {
			return;
		}
		try {
			Files.setPosixFilePermissions(mvnw, PosixFilePermissions.fromString("rwxr-xr-x"));
			logger.debug("Made executable: {}", mvnw);
		}
		catch (UnsupportedOperationException ex) {
			logger.debug("POSIX permissions not supported (Windows?), skipping chmod on {}", mvnw);
		}
		catch (IOException ex) {
			logger.warn("Could not set execute permission on {}: {}", mvnw, ex.getMessage());
		}
	}

}
