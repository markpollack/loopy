package io.github.markpollack.loopy.boot;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;
import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slash command for discovering and inspecting Agent Starters.
 *
 * <p>
 * Agent Starters ({@code spring-ai-starter-{domain}}) are full packages — skill + SAE +
 * tools + auto-config. This command is SEPARATE from {@code /skills}, which deals with
 * knowledge-only skill files.
 * </p>
 *
 * <ul>
 * <li>{@code /starters} or {@code /starters list} — list catalog entries</li>
 * <li>{@code /starters search <query>} — search by name/description/trigger</li>
 * <li>{@code /starters info <name>} — show full entry details</li>
 * <li>{@code /starters suggest} — scan pom.xml in working directory and suggest relevant
 * starters</li>
 * </ul>
 */
public class StartersCommand implements SlashCommand {

	private static final Logger logger = LoggerFactory.getLogger(StartersCommand.class);

	private static final Style NAME_STYLE = Style.newStyle().foreground(Color.color("#7AA2F7")).bold(true);

	private static final Style DESC_STYLE = Style.newStyle().foreground(Color.color("#A9B1D6"));

	private static final Style META_STYLE = Style.newStyle().faint(true);

	private static final Style HINT_STYLE = Style.newStyle().foreground(Color.color("#565F89"));

	private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("<artifactId>([^<]+)</artifactId>");

	@Override
	public String name() {
		return "starters";
	}

	@Override
	public String description() {
		return "Discover and inspect Agent Starters for domain capabilities";
	}

	@Override
	public String execute(String args, CommandContext context) {
		String[] parts = args.strip().split("\\s+", 2);
		String subcommand = parts[0];
		String subArgs = parts.length > 1 ? parts[1].strip() : "";

		if (subcommand.isEmpty() || "list".equals(subcommand)) {
			return listStarters();
		}
		else if ("search".equals(subcommand)) {
			return searchCatalog(subArgs);
		}
		else if ("info".equals(subcommand)) {
			if (subArgs.isEmpty()) {
				return "Usage: /starters info <name>";
			}
			return showStarter(subArgs);
		}
		else if ("suggest".equals(subcommand)) {
			return suggestStarters(context.workingDirectory());
		}
		else {
			return "Unknown subcommand: " + subcommand
					+ ". Usage: /starters [list | search <query> | info <name> | suggest]";
		}
	}

	private String listStarters() {
		var catalog = StartersCatalog.load();
		List<StartersCatalog.StarterEntry> entries = catalog.all();
		if (entries.isEmpty()) {
			StringBuilder sb = new StringBuilder("No Agent Starters in catalog yet.\n\n");
			sb.append(HINT_STYLE.render("  Agent Starters are spring-ai-starter-{domain} Maven dependencies.\n"));
			sb.append(
					HINT_STYLE.render("  Use /starters suggest to detect relevant starters for your current project."));
			return sb.toString();
		}

		StringBuilder sb = new StringBuilder("Agent Starters (" + entries.size() + "):\n\n");
		for (var entry : entries) {
			sb.append("  ").append(NAME_STYLE.render(entry.name != null ? entry.name : "(unnamed)")).append("\n");
			if (entry.description != null) {
				sb.append("      ").append(DESC_STYLE.render(entry.description)).append("\n");
			}
			if (entry.mavenCoordinates != null) {
				sb.append("      ").append(META_STYLE.render(entry.mavenCoordinates)).append("\n");
			}
		}
		return sb.toString().stripTrailing();
	}

	private String searchCatalog(String query) {
		var catalog = StartersCatalog.load();
		var results = query.isBlank() ? catalog.all() : catalog.search(query);
		if (results.isEmpty()) {
			if (catalog.all().isEmpty()) {
				return "Catalog is empty. Agent Starters are built as domain expertise grows.";
			}
			return "No starters match '" + query + "'. Try /starters search with a broader term.";
		}

		String header = query.isBlank() ? "All starters (" + results.size() + "):"
				: results.size() + " result" + (results.size() == 1 ? "" : "s") + " for '" + query + "':";
		StringBuilder sb = new StringBuilder(header).append("\n");
		for (var entry : results) {
			sb.append("\n  ").append(NAME_STYLE.render(entry.name != null ? entry.name : "(unnamed)")).append("\n");
			if (entry.description != null) {
				sb.append("      ").append(DESC_STYLE.render(entry.description)).append("\n");
			}
			if (entry.triggers != null && !entry.triggers.isEmpty()) {
				sb.append("      ")
					.append(META_STYLE.render("triggers: " + String.join(", ", entry.triggers)))
					.append("\n");
			}
			if (entry.mavenCoordinates != null) {
				sb.append("      ").append(META_STYLE.render(entry.mavenCoordinates)).append("\n");
			}
		}
		sb.append("\n").append(HINT_STYLE.render("  /starters info <name> for details"));
		return sb.toString();
	}

	private String showStarter(String name) {
		var catalog = StartersCatalog.load();
		var found = catalog.findByName(name);
		if (found.isEmpty()) {
			return "Starter '" + name + "' not found. Use /starters search to browse available starters.";
		}

		var entry = found.get();
		StringBuilder sb = new StringBuilder();
		sb.append(NAME_STYLE.render(entry.name != null ? entry.name : "(unnamed)")).append("\n");
		if (entry.description != null) {
			sb.append(DESC_STYLE.render(entry.description)).append("\n");
		}
		if (entry.version != null) {
			sb.append(META_STYLE.render("version: " + entry.version)).append("\n");
		}
		if (entry.triggers != null && !entry.triggers.isEmpty()) {
			sb.append(META_STYLE.render("triggers on: " + String.join(", ", entry.triggers))).append("\n");
		}
		if (entry.commands != null && !entry.commands.isEmpty()) {
			sb.append(META_STYLE.render("enables: " + String.join(", ", entry.commands))).append("\n");
		}
		if (entry.mavenCoordinates != null) {
			sb.append("\nInstall via Maven:\n");
			sb.append(formatMavenDep(entry.mavenCoordinates));
		}
		if (entry.name != null) {
			sb.append("\nInstall via Loopy:  /boot-add ").append(entry.name);
		}
		return sb.toString().stripTrailing();
	}

	private String suggestStarters(Path workingDirectory) {
		Path pomFile = workingDirectory.resolve("pom.xml");
		if (!Files.isRegularFile(pomFile)) {
			return "No pom.xml found in " + workingDirectory
					+ ". Navigate to a Maven project directory, or specify -d <dir>.";
		}

		List<String> artifactIds = extractArtifactIds(pomFile);
		if (artifactIds.isEmpty()) {
			return "Could not read artifactIds from " + pomFile;
		}

		var catalog = StartersCatalog.load();
		List<StartersCatalog.StarterEntry> suggestions = catalog.findByTriggers(artifactIds);

		if (catalog.all().isEmpty()) {
			return "Catalog is empty — no starters to suggest yet.\n"
					+ META_STYLE.render("(" + artifactIds.size() + " dependencies scanned from pom.xml)");
		}

		if (suggestions.isEmpty()) {
			return "No Agent Starters match your project's dependencies.\n"
					+ META_STYLE.render("(" + artifactIds.size() + " dependencies scanned)");
		}

		StringBuilder sb = new StringBuilder(
				"Suggested Agent Starters for your project (" + suggestions.size() + "):\n");
		for (var entry : suggestions) {
			sb.append("\n  ").append(NAME_STYLE.render(entry.name != null ? entry.name : "(unnamed)")).append("\n");
			if (entry.description != null) {
				sb.append("      ").append(DESC_STYLE.render(entry.description)).append("\n");
			}
			sb.append("      ")
				.append(HINT_STYLE.render("/boot-add " + (entry.name != null ? entry.name : "")))
				.append("\n");
		}
		return sb.toString().stripTrailing();
	}

	/**
	 * Extract all {@code <artifactId>} values from a pom.xml file.
	 */
	static List<String> extractArtifactIds(Path pomFile) {
		List<String> ids = new ArrayList<>();
		try {
			String content = Files.readString(pomFile);
			Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content);
			while (matcher.find()) {
				ids.add(matcher.group(1).trim());
			}
		}
		catch (IOException ex) {
			logger.debug("Could not read pom.xml at {}: {}", pomFile, ex.getMessage());
		}
		return ids;
	}

	private static String formatMavenDep(@Nullable String coordinates) {
		if (coordinates == null) {
			return "";
		}
		String[] parts = coordinates.split(":");
		if (parts.length < 2) {
			return "  " + coordinates + "\n";
		}
		StringBuilder sb = new StringBuilder("  <dependency>\n");
		sb.append("    <groupId>").append(parts[0]).append("</groupId>\n");
		sb.append("    <artifactId>").append(parts[1]).append("</artifactId>\n");
		if (parts.length >= 3) {
			sb.append("    <version>").append(parts[2]).append("</version>\n");
		}
		sb.append("  </dependency>\n");
		return sb.toString();
	}

}
