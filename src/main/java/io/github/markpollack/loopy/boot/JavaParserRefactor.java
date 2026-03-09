package io.github.markpollack.loopy.boot;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Java package rename using JavaParser AST.
 *
 * <p>
 * Walks all {@code .java} files under a project root, rewrites {@code package} and
 * {@code import} declarations from {@code fromPackage} to {@code toPackage}, and moves
 * the files to match the new directory structure. Formatting is preserved via
 * {@link LexicalPreservingPrinter}.
 * </p>
 */
public class JavaParserRefactor {

	private static final Logger logger = LoggerFactory.getLogger(JavaParserRefactor.class);

	private final JavaParser parser = new JavaParser(
			new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25));

	/**
	 * Rename all occurrences of {@code fromPackage} to {@code toPackage} in the given
	 * project root.
	 * @param projectRoot root of the Maven project
	 * @param fromPackage fully-qualified source package (e.g., {@code com.example.app})
	 * @param toPackage fully-qualified target package (e.g., {@code com.acme.widget})
	 */
	public void refactorPackage(Path projectRoot, String fromPackage, String toPackage) {
		String fromPathSeg = fromPackage.replace('.', '/');
		String toPathSeg = toPackage.replace('.', '/');

		List<Path> javaFiles = collectJavaFiles(projectRoot);
		for (Path file : javaFiles) {
			try {
				refactorFile(file, fromPackage, toPackage, fromPathSeg, toPathSeg);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Failed to refactor " + file, ex);
			}
		}
		deleteEmptyDirectories(projectRoot, fromPathSeg);
	}

	private List<Path> collectJavaFiles(Path root) {
		List<Path> files = new ArrayList<>();
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.toString().endsWith(".java")) {
						files.add(file);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to walk project tree: " + root, ex);
		}
		return files;
	}

	private void refactorFile(Path file, String fromPackage, String toPackage, String fromPathSeg, String toPathSeg)
			throws IOException {
		String source = Files.readString(file, StandardCharsets.UTF_8);
		ParseResult<CompilationUnit> parseResult = parser.parse(source);

		if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
			logger.warn("Skipping unparseable file: {}", file);
			return;
		}

		CompilationUnit cu = parseResult.getResult().get();
		LexicalPreservingPrinter.setup(cu);

		boolean modified = rewritePackageDeclaration(cu, fromPackage, toPackage);
		modified |= rewriteImports(cu, fromPackage, toPackage);

		Path newFile = computeNewPath(file, fromPathSeg, toPathSeg);
		boolean moved = !newFile.equals(file);

		if (modified) {
			String result = LexicalPreservingPrinter.print(cu);
			if (moved) {
				Files.createDirectories(newFile.getParent());
				Files.writeString(newFile, result, StandardCharsets.UTF_8);
				Files.delete(file);
				logger.debug("Moved + refactored: {} -> {}", file, newFile);
			}
			else {
				Files.writeString(file, result, StandardCharsets.UTF_8);
				logger.debug("Refactored in-place: {}", file);
			}
		}
		else if (moved) {
			Files.createDirectories(newFile.getParent());
			Files.move(file, newFile, StandardCopyOption.REPLACE_EXISTING);
			logger.debug("Moved (no content change): {} -> {}", file, newFile);
		}
	}

	private boolean rewritePackageDeclaration(CompilationUnit cu, String fromPackage, String toPackage) {
		return cu.getPackageDeclaration().map(pkgDecl -> {
			String pkg = pkgDecl.getNameAsString();
			if (pkg.equals(fromPackage)) {
				pkgDecl.setName(toPackage);
				return true;
			}
			if (pkg.startsWith(fromPackage + ".")) {
				pkgDecl.setName(toPackage + pkg.substring(fromPackage.length()));
				return true;
			}
			return false;
		}).orElse(false);
	}

	private boolean rewriteImports(CompilationUnit cu, String fromPackage, String toPackage) {
		boolean modified = false;
		for (var imp : cu.getImports()) {
			String name = imp.getNameAsString();
			if (name.equals(fromPackage)) {
				imp.setName(toPackage);
				modified = true;
			}
			else if (name.startsWith(fromPackage + ".")) {
				imp.setName(toPackage + name.substring(fromPackage.length()));
				modified = true;
			}
		}
		return modified;
	}

	/**
	 * Compute the new file path by replacing the {@code fromPathSeg} directory segment.
	 */
	private Path computeNewPath(Path file, String fromPathSeg, String toPathSeg) {
		String fileStr = file.toString();
		String pattern = "/" + fromPathSeg + "/";
		int idx = fileStr.indexOf(pattern);
		if (idx < 0) {
			return file;
		}
		// fileStr.substring(0, idx + 1) includes the leading '/'
		// fileStr.substring(idx + pattern.length() - 1) starts at the trailing '/'
		String newStr = fileStr.substring(0, idx + 1) + toPathSeg + fileStr.substring(idx + pattern.length() - 1);
		return Path.of(newStr);
	}

	/**
	 * Delete empty directories left behind by file moves (deepest first).
	 */
	private void deleteEmptyDirectories(Path root, String fromPathSeg) {
		// Collect directories that match the old package path, deepest first
		List<Path> toDelete = new ArrayList<>();
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.toString().contains("/" + fromPathSeg)) {
						toDelete.add(dir);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			logger.warn("Could not walk for cleanup: {}", ex.getMessage());
			return;
		}

		// Delete deepest first
		toDelete.sort((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()));
		for (Path dir : toDelete) {
			try {
				if (Files.isDirectory(dir)) {
					try (var stream = Files.list(dir)) {
						if (stream.findFirst().isEmpty()) {
							Files.delete(dir);
							logger.debug("Deleted empty directory: {}", dir);
						}
					}
				}
			}
			catch (IOException ex) {
				logger.warn("Could not delete directory {}: {}", dir, ex.getMessage());
			}
		}
	}

}
