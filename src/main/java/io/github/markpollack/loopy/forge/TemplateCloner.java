package io.github.markpollack.loopy.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clones the agent-experiment-template repository to create a new project. Uses
 * {@code git clone --depth 1} for efficiency, then removes the .git directory so the
 * project starts with a clean history.
 */
public class TemplateCloner {

	private static final Logger logger = LoggerFactory.getLogger(TemplateCloner.class);

	/**
	 * Clone the template repository to the output directory.
	 * @param templateRepoUrl URL of the template repository
	 * @param outputDir target directory for the new project
	 */
	public void cloneTemplate(String templateRepoUrl, Path outputDir) {
		try {
			if (Files.exists(outputDir) && Files.list(outputDir).findAny().isPresent()) {
				throw new IllegalStateException("Output directory is not empty: " + outputDir);
			}

			Files.createDirectories(outputDir);

			logger.info("Cloning template repository...");
			ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", templateRepoUrl,
					outputDir.toString());
			pb.inheritIO();
			Process process = pb.start();
			boolean completed = process.waitFor(2, TimeUnit.MINUTES);

			if (!completed) {
				process.destroyForcibly();
				throw new RuntimeException("Git clone timed out after 2 minutes");
			}

			if (process.exitValue() != 0) {
				throw new RuntimeException("Git clone failed with exit code: " + process.exitValue());
			}

			// Remove .git directory so project starts with clean history
			Path gitDir = outputDir.resolve(".git");
			if (Files.exists(gitDir)) {
				deleteRecursive(gitDir);
			}

			// Initialize fresh git repo
			ProcessBuilder initPb = new ProcessBuilder("git", "init");
			initPb.directory(outputDir.toFile());
			initPb.inheritIO();
			Process initProcess = initPb.start();
			initProcess.waitFor(30, TimeUnit.SECONDS);

			logger.info("Template cloned to {}", outputDir);
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException("Failed to clone template", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Git clone interrupted", ex);
		}
	}

	private void deleteRecursive(Path path) throws IOException {
		Files.walk(path).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
			try {
				Files.delete(p);
			}
			catch (IOException ex) {
				logger.warn("Failed to delete: {}", p);
			}
		});
	}

}
