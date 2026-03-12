package io.github.markpollack.loopy.boot;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /boot-setup} — guided one-time preferences wizard for {@code /boot-new}.
 *
 * <p>
 * Two-pass interaction:
 * </p>
 * <ol>
 * <li>No args → returns a formatted question form showing current preferences and
 * available curated dependencies (top 5 by number + free-form {@code --more} field)</li>
 * <li>With args → processes answers, resolves {@code --more} via
 * {@link BootDependencyClassifier} (Haiku), saves to
 * {@code ~/.config/loopy/boot/preferences.yml}</li>
 * </ol>
 *
 * <pre>
 * /boot-setup
 * /boot-setup --group com.acme --java 21 --add 1,2 --more "redis and flyway" --db postgres
 * </pre>
 */
public class BootSetupCommand implements SlashCommand {

	/**
	 * Top-5 curated dependencies offered as numbered shortcuts in the question form.
	 * Stored in prefs as {@code groupId:artifactId}.
	 */
	public static final List<CuratedDep> CURATED_DEPS = List.of(
			new CuratedDep(1, "org.springframework.boot", "spring-boot-starter-actuator",
					"Health, metrics, management endpoints", "dependency"),
			new CuratedDep(2, "org.graalvm.buildtools", "native-maven-plugin", "GraalVM native image / AOT compilation",
					"plugin"),
			new CuratedDep(3, "org.springframework.boot", "spring-boot-starter-security",
					"Authentication and authorization", "dependency"),
			new CuratedDep(4, "org.springframework.boot", "spring-boot-starter-data-jpa",
					"JPA/Hibernate ORM — relational database access", "dependency"),
			new CuratedDep(5, "org.springframework.boot", "spring-boot-starter-data-jdbc",
					"Spring Data JDBC — lighter relational data access, no ORM", "dependency"));

	/** A curated dependency or plugin with its numbered shortcut. */
	public record CuratedDep(int number, String groupId, String artifactId, String description, String type) {

		/**
		 * Canonical coordinate used in preferences storage. Plugins are prefixed with
		 * {@code plugin:} to distinguish them from dependencies.
		 */
		public String coords() {
			return "plugin".equals(type) ? "plugin:" + groupId + ":" + artifactId : groupId + ":" + artifactId;
		}

	}

	private final @Nullable ChatModel chatModel;

	public BootSetupCommand(@Nullable ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "boot-setup";
	}

	@Override
	public String description() {
		return "Set up persistent defaults for /boot-new (groupId, Java version, always-add deps)";
	}

	@Override
	public boolean requiresArguments() {
		return false;
	}

	@Override
	public String execute(@Nullable String args, CommandContext context) {
		Map<String, String> flags = parseFlags(args);

		if (flags.isEmpty()) {
			return questionForm();
		}
		return processAnswers(flags);
	}

	// ── Question form ─────────────────────────────────────────────────────────────

	private static String questionForm() {
		BootPreferences existing = BootPreferences.load();
		StringBuilder sb = new StringBuilder();
		sb.append("Boot Setup — answer all at once with one command (see example at the bottom).\n");

		sb.append("\n1. Default groupId (Maven package prefix)?\n");
		sb.append("   Current: ").append(existing.groupId() != null ? existing.groupId() : "(not set)").append("\n");

		sb.append("\n2. Preferred Java version?  [17 / 21 / 23]\n");
		sb.append("   Current: ").append(existing.javaVersion() != null ? existing.javaVersion() : "21").append("\n");

		sb.append("\n3. Dependencies to always add to every new project?\n");
		sb.append("   Select by number (multiple ok), and/or describe anything else in --more:\n");
		for (CuratedDep dep : CURATED_DEPS) {
			sb.append("   [").append(dep.number()).append("] ").append(dep.artifactId());
			sb.append(" — ").append(dep.description()).append("\n");
		}
		if (!existing.alwaysAdd().isEmpty()) {
			sb.append("   Current: ").append(String.join(", ", existing.alwaysAdd())).append("\n");
		}

		sb.append("\n4. Preferred database?  [h2 / postgres / mysql / mongodb / none]\n");
		sb.append("   Current: ")
			.append(existing.preferDatabase() != null ? existing.preferDatabase() : "(none)")
			.append("\n");

		sb.append("""

				Reply with (omit any flag to keep current value):
				  /boot-setup --group <groupId> --java <version> --add <numbers> --more "<free text>" --db <database>

				Example:
				  /boot-setup --group com.acme --java 21 --add 1,2 --more "redis and flyway" --db postgres
				""");
		return sb.toString();
	}

	// ── Process answers ───────────────────────────────────────────────────────────

	private String processAnswers(Map<String, String> flags) {
		BootPreferences existing = BootPreferences.load();

		String groupId = flags.getOrDefault("group", existing.groupId());
		String javaVersion = flags.getOrDefault("java", existing.javaVersion() != null ? existing.javaVersion() : "21");
		String preferDatabase = flags.getOrDefault("db", existing.preferDatabase());

		// Start from existing alwaysAdd, add curated shortcuts, then NL --more
		List<String> alwaysAdd = new ArrayList<>(existing.alwaysAdd());

		String addNums = flags.get("add");
		if (addNums != null && !addNums.isBlank()) {
			for (String numStr : addNums.split(",")) {
				try {
					int n = Integer.parseInt(numStr.trim());
					CURATED_DEPS.stream()
						.filter(d -> d.number() == n)
						.map(CuratedDep::coords)
						.filter(c -> !alwaysAdd.contains(c))
						.forEach(alwaysAdd::add);
				}
				catch (NumberFormatException ignored) {
				}
			}
		}

		String more = flags.get("more");
		if (more != null && !more.isBlank()) {
			if (chatModel != null) {
				new BootDependencyClassifier(chatModel).classify(more)
					.stream()
					.filter(c -> !alwaysAdd.contains(c))
					.forEach(alwaysAdd::add);
			}
			else {
				return "Cannot resolve --more without an LLM (ANTHROPIC_API_KEY not set?). "
						+ "Use --add with numbered shortcuts instead.";
			}
		}

		if (groupId == null) {
			return "Error: --group is required on first setup.\n" + "Example: /boot-setup --group com.acme --java 21";
		}

		new BootPreferences(javaVersion, groupId, alwaysAdd, preferDatabase).save();

		StringBuilder result = new StringBuilder("Preferences saved to ~/.config/loopy/boot/preferences.yml\n\n");
		result.append("  groupId:        ").append(groupId).append("\n");
		result.append("  javaVersion:    ").append(javaVersion).append("\n");
		result.append("  alwaysAdd:      ")
			.append(alwaysAdd.isEmpty() ? "(none)" : String.join(", ", alwaysAdd))
			.append("\n");
		result.append("  preferDatabase: ").append(preferDatabase != null ? preferDatabase : "(none)").append("\n");
		result.append("\nReady. Try: /boot-new --name my-app");
		return result.toString();
	}

	// ── Flag parsing (handles quoted values) ─────────────────────────────────────

	static Map<String, String> parseFlags(@Nullable String args) {
		Map<String, String> result = new LinkedHashMap<>();
		if (args == null || args.isBlank()) {
			return result;
		}
		List<String> tokens = tokenize(args);
		for (int i = 0; i < tokens.size(); i++) {
			String t = tokens.get(i);
			if (t.startsWith("--")) {
				String key = t.substring(2);
				if (i + 1 < tokens.size() && !tokens.get(i + 1).startsWith("--")) {
					result.put(key, tokens.get(i + 1));
					i++;
				}
				else {
					result.put(key, "true");
				}
			}
		}
		return result;
	}

	/** Tokenize a string respecting double-quoted spans. */
	static List<String> tokenize(String args) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (char c : args.toCharArray()) {
			if (c == '"') {
				inQuotes = !inQuotes;
			}
			else if (c == ' ' && !inQuotes) {
				if (!current.isEmpty()) {
					tokens.add(current.toString());
					current.setLength(0);
				}
			}
			else {
				current.append(c);
			}
		}
		if (!current.isEmpty()) {
			tokens.add(current.toString());
		}
		return tokens;
	}

}
