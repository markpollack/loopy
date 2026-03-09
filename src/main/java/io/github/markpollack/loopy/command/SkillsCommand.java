package io.github.markpollack.loopy.command;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;
import org.springaicommunity.agent.tools.SkillsTool.Skill;
import org.springaicommunity.agent.utils.MarkdownParser;
import org.springaicommunity.agent.utils.Skills;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Slash command for discovering and inspecting domain skills.
 * <p>
 * Scans {@code .claude/skills/} in the working directory and {@code ~/.claude/skills/}
 * globally for {@code SKILL.md} files.
 *
 * <ul>
 * <li>{@code /skills} or {@code /skills list} — list discovered skills</li>
 * <li>{@code /skills search <query>} — search the curated catalog</li>
 * <li>{@code /skills info <name>} — show full skill content</li>
 * <li>{@code /skills add <name>} — install a skill from the catalog</li>
 * <li>{@code /skills remove <name>} — uninstall a skill</li>
 * </ul>
 */
public class SkillsCommand implements SlashCommand {

	private static final Style NAME_STYLE = Style.newStyle().foreground(Color.color("#7AA2F7")).bold(true);

	private static final Style DESC_STYLE = Style.newStyle().foreground(Color.color("#A9B1D6"));

	private static final Style META_STYLE = Style.newStyle().faint(true);

	private static final Style HINT_STYLE = Style.newStyle().foreground(Color.color("#565F89"));

	private static final Style INSTALLED_STYLE = Style.newStyle().foreground(Color.color("#9ECE6A"));

	@Override
	public String name() {
		return "skills";
	}

	@Override
	public String description() {
		return "List and inspect domain skills";
	}

	@Override
	public String execute(String args, CommandContext context) {
		String[] parts = args.strip().split("\\s+", 2);
		String subcommand = parts[0];
		String subArgs = parts.length > 1 ? parts[1].strip() : "";

		if (subcommand.isEmpty() || "list".equals(subcommand)) {
			return listSkills(context);
		}
		else if ("search".equals(subcommand)) {
			return searchCatalog(subArgs);
		}
		else if ("info".equals(subcommand)) {
			if (subArgs.isEmpty()) {
				return "Usage: /skills info <name>";
			}
			return showSkill(subArgs, context);
		}
		else if ("add".equals(subcommand)) {
			if (subArgs.isEmpty()) {
				return "Usage: /skills add <name>";
			}
			return addSkill(subArgs);
		}
		else if ("remove".equals(subcommand)) {
			if (subArgs.isEmpty()) {
				return "Usage: /skills remove <name>";
			}
			return removeSkill(subArgs);
		}
		else {
			return "Unknown subcommand: " + subcommand
					+ ". Usage: /skills [list | search <query> | info <name> | add <name> | remove <name>]";
		}
	}

	private String listSkills(CommandContext context) {
		var catalog = SkillsCatalog.load();
		int catalogSize = catalog.all().size();
		List<SkillEntry> skills = discoverSkills(context);
		if (skills.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("No skills installed.\n\n");
			sb.append(HINT_STYLE.render("  Browse " + catalogSize + " available:"))
				.append("  /skills search <query>\n");
			sb.append(HINT_STYLE.render("  List all:")).append("              /skills search\n");
			sb.append(HINT_STYLE.render("  Add a skill:")).append("           /skills add <name>");
			return sb.toString();
		}

		StringBuilder sb = new StringBuilder("Installed skills:\n\n");
		for (SkillEntry skill : skills) {
			sb.append("  ").append(NAME_STYLE.render(skill.name));
			sb.append(META_STYLE.render(" (" + skill.source + ")")).append("\n");
			if (skill.description != null) {
				sb.append("      ").append(DESC_STYLE.render(skill.description)).append("\n");
			}
		}
		return sb.toString().stripTrailing();
	}

	private String showSkill(String name, CommandContext context) {
		// Check installed/discovered skills first
		List<SkillEntry> skills = discoverSkills(context);
		for (SkillEntry skill : skills) {
			if (name.equalsIgnoreCase(skill.name)) {
				StringBuilder sb = new StringBuilder();
				sb.append(NAME_STYLE.render(skill.name));
				sb.append(INSTALLED_STYLE.render(" (installed)")).append("\n");
				if (skill.description != null) {
					sb.append(DESC_STYLE.render(skill.description)).append("\n");
				}
				sb.append(META_STYLE.render("Source: " + (skill.path != null ? skill.path : skill.source)))
					.append("\n");
				appendCatalogInstallPaths(sb, skill.name);
				sb.append("\n").append(skill.content);
				return sb.toString().stripTrailing();
			}
		}

		// Fall back to catalog entry (skill not installed but in catalog)
		var catalog = SkillsCatalog.load();
		var catalogEntry = catalog.findByName(name);
		if (catalogEntry.isPresent()) {
			var entry = catalogEntry.get();
			StringBuilder sb = new StringBuilder();
			sb.append(NAME_STYLE.render(entry.name)).append("\n");
			if (entry.description != null) {
				sb.append(DESC_STYLE.render(entry.description)).append("\n");
			}
			sb.append(META_STYLE.render("by " + entry.author)).append("\n");
			if (entry.tags != null && !entry.tags.isEmpty()) {
				sb.append(META_STYLE.render("tags: " + String.join(", ", entry.tags))).append("\n");
			}
			sb.append("\n");
			appendInstallPaths(sb, entry);
			return sb.toString().stripTrailing();
		}

		return "Skill not found: " + name + ". Use /skills search to browse available skills.";
	}

	private void appendCatalogInstallPaths(StringBuilder sb, String skillName) {
		var catalog = SkillsCatalog.load();
		var entry = catalog.findByName(skillName);
		if (entry.isPresent()) {
			sb.append("\n");
			appendInstallPaths(sb, entry.get());
		}
	}

	private void appendInstallPaths(StringBuilder sb, SkillsCatalog.CatalogEntry entry) {
		sb.append("Install via Loopy:  /skills add ").append(entry.name).append("\n");
		if (entry.skillsjars != null && !entry.skillsjars.isBlank()) {
			sb.append("Install via Maven:  <dependency>\n");
			sb.append("                      <groupId>com.skillsjars</groupId>\n");
			sb.append("                      <artifactId>")
				.append(entry.skillsjars.replace("com.skillsjars:", ""))
				.append("</artifactId>\n");
			sb.append("                    </dependency>\n");
		}
	}

	private List<SkillEntry> discoverSkills(CommandContext context) {
		List<SkillEntry> skills = new ArrayList<>();

		// Project-level skills
		Path projectDir = context.workingDirectory().resolve(".claude/skills");
		if (Files.isDirectory(projectDir)) {
			scanDirectory(projectDir, "project", skills);
		}

		// Global skills
		Path globalDir = Path.of(System.getProperty("user.home"), ".claude", "skills");
		if (Files.isDirectory(globalDir)) {
			scanDirectory(globalDir, "global", skills);
		}

		// Classpath skills (SkillsJars — Maven dependencies with SKILL.md in JARs)
		for (String prefix : List.of("META-INF/skills", "META-INF/resources/skills")) {
			try {
				List<Skill> classpathSkills = Skills.loadResource(new ClassPathResource(prefix));
				for (Skill skill : classpathSkills) {
					String name = skill.frontMatter().containsKey("name") ? skill.name() : skill.basePath();
					String desc = skill.frontMatter().containsKey("description")
							? skill.frontMatter().get("description").toString() : null;
					skills.add(new SkillEntry(name, desc, "classpath", null, skill.content()));
				}
			}
			catch (Exception ex) {
				// No skills at this prefix — expected
			}
		}

		return skills;
	}

	private void scanDirectory(Path dir, String source, List<SkillEntry> skills) {
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.filter(p -> p.getFileName().toString().equals("SKILL.md")).forEach(skillFile -> {
				try {
					String raw = Files.readString(skillFile);
					var parsed = new MarkdownParser(raw);
					Map<String, Object> frontMatter = parsed.getFrontMatter();
					String skillName = frontMatter.containsKey("name") ? frontMatter.get("name").toString()
							: skillFile.getParent().getFileName().toString();
					String description = frontMatter.containsKey("description")
							? frontMatter.get("description").toString() : null;
					skills.add(new SkillEntry(skillName, description, source, skillFile, parsed.getContent()));
				}
				catch (IOException ex) {
					// Skip unreadable files
				}
			});
		}
		catch (IOException ex) {
			// Skip inaccessible directories
		}
	}

	private String searchCatalog(String query) {
		var catalog = SkillsCatalog.load();
		var results = query.isBlank() ? catalog.all() : catalog.search(query);
		if (results.isEmpty()) {
			return "No skills match '" + query + "'. Try /skills search with a broader term.";
		}

		String header = query.isBlank() ? "All skills (" + results.size() + "):"
				: results.size() + " result" + (results.size() == 1 ? "" : "s") + " for '" + query + "':";
		StringBuilder sb = new StringBuilder(header).append("\n");
		for (var entry : results) {
			boolean installed = SkillsCatalog.isInstalled(entry.name);
			sb.append("\n  ");
			if (installed) {
				sb.append(INSTALLED_STYLE.render("✔ "));
			}
			sb.append(NAME_STYLE.render(entry.name)).append("\n");
			if (entry.description != null) {
				sb.append("      ").append(DESC_STYLE.render(entry.description)).append("\n");
			}
			sb.append("      ").append(META_STYLE.render("by " + entry.author));
			if (entry.tags != null && !entry.tags.isEmpty()) {
				sb.append(META_STYLE.render(" · " + String.join(", ", entry.tags)));
			}
			sb.append("\n");
		}
		sb.append("\n").append(HINT_STYLE.render("  /skills info <name> for details · /skills add <name> to install"));
		return sb.toString();
	}

	private String addSkill(String name) {
		var catalog = SkillsCatalog.load();
		var entry = catalog.findByName(name);
		if (entry.isEmpty()) {
			return "Skill '" + name + "' not found in catalog. Use /skills search to find available skills.";
		}

		if (SkillsCatalog.isInstalled(name)) {
			return "Skill '" + name + "' is already installed at ~/.claude/skills/" + name + "/";
		}

		var skill = entry.get();
		if (skill.source == null || !"github".equals(skill.source.type)) {
			return "Skill '" + name + "' cannot be installed automatically (source type: "
					+ (skill.source != null ? skill.source.type : "unknown")
					+ "). Install manually from the source repository.";
		}

		// Download SKILL.md from GitHub raw content
		String rawUrl = "https://raw.githubusercontent.com/" + skill.source.repo + "/" + skill.source.branch + "/"
				+ skill.source.path + "/SKILL.md";

		Path installDir = Path.of(System.getProperty("user.home"), ".claude", "skills", name);
		try {
			Files.createDirectories(installDir);

			// Use ProcessBuilder to download with curl (available on all platforms)
			var process = new ProcessBuilder("curl", "-sL", "-o", installDir.resolve("SKILL.md").toString(), rawUrl)
				.redirectErrorStream(true)
				.start();
			int exitCode = process.waitFor();

			if (exitCode != 0 || !Files.exists(installDir.resolve("SKILL.md"))
					|| Files.size(installDir.resolve("SKILL.md")) == 0) {
				// Clean up failed install
				Files.deleteIfExists(installDir.resolve("SKILL.md"));
				Files.deleteIfExists(installDir);
				return "Failed to download skill '" + name + "' from " + rawUrl;
			}

			return "Installed '" + name + "' to ~/.claude/skills/" + name
					+ "/\nSkill will be available on next agent start.";
		}
		catch (IOException | InterruptedException ex) {
			return "Error installing skill '" + name + "': " + ex.getMessage();
		}
	}

	private String removeSkill(String name) {
		Path skillDir = Path.of(System.getProperty("user.home"), ".claude", "skills", name);
		if (!Files.isDirectory(skillDir)) {
			return "Skill '" + name + "' is not installed.";
		}

		try (Stream<Path> paths = Files.walk(skillDir)) {
			// Delete files first, then directories (reverse order)
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				}
				catch (IOException ex) {
					// best effort
				}
			});
			return "Removed '" + name + "' from ~/.claude/skills/" + name + "/";
		}
		catch (IOException ex) {
			return "Error removing skill '" + name + "': " + ex.getMessage();
		}
	}

	private record SkillEntry(String name, String description, String source, Path path, String content) {
	}

}
