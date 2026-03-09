package io.github.markpollack.loopy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages API key configuration in {@code ~/.config/loopy/config}.
 * <p>
 * On first run, if the required API key is not set in the environment, the user is
 * prompted to enter it. Keys are stored as {@code KEY=value} lines.
 */
public class LoopyConfig {

	static final Path CONFIG_FILE = Path.of(System.getProperty("user.home"), ".config", "loopy", "config");

	/**
	 * Returns the Spring AI property name for the given provider's API key.
	 */
	static String springPropertyFor(String provider) {
		return switch (provider) {
			case "openai" -> "spring.ai.openai.api-key";
			case "google-genai" -> "spring.ai.google.genai.api-key";
			default -> "spring.ai.anthropic.api-key";
		};
	}

	/**
	 * Returns the environment variable name for the given provider's API key.
	 */
	static String envVarFor(String provider) {
		return switch (provider) {
			case "openai" -> "OPENAI_API_KEY";
			case "google-genai" -> "GOOGLE_API_KEY";
			default -> "ANTHROPIC_API_KEY";
		};
	}

	/**
	 * Returns true if an API key is available for the given provider (env, dotenv, or
	 * saved config). Does not prompt the user.
	 */
	static boolean hasKey(String provider) {
		String envVar = envVarFor(provider);
		String key = System.getenv(envVar);
		if (key != null && !key.isBlank()) {
			return true;
		}
		Map<String, String> dotenv = parseDotEnv(Path.of(System.getProperty("user.home"), ".env"));
		key = dotenv.get(envVar);
		if (key != null && !key.isBlank()) {
			return true;
		}
		Map<String, String> saved = load();
		key = saved.get(envVar);
		return key != null && !key.isBlank();
	}

	/**
	 * Ensures an API key is available for the given provider. Checks (in order): 1.
	 * Environment variable 2. {@code ~/.config/loopy/config} 3. Interactive prompt (saved
	 * to config for future runs)
	 * <p>
	 * If a key is found, sets it as a system property so Spring AI picks it up.
	 */
	static void ensureApiKey(String provider) {
		String envVar = envVarFor(provider);
		String springProp = springPropertyFor(provider);

		// 1. Already in environment (exported vars)
		String key = System.getenv(envVar);
		if (key != null && !key.isBlank()) {
			System.setProperty(springProp, key);
			return;
		}

		// 2. ~/.env (KEY=value without export — common dotenv format)
		Map<String, String> dotenv = parseDotEnv(Path.of(System.getProperty("user.home"), ".env"));
		key = dotenv.get(envVar);
		if (key != null && !key.isBlank()) {
			System.setProperty(springProp, key);
			return;
		}

		// 3. Saved in config file
		Map<String, String> saved = load();
		key = saved.get(envVar);
		if (key != null && !key.isBlank()) {
			System.setProperty(springProp, key);
			return;
		}

		// 4. Prompt the user
		key = promptForKey(provider, envVar);
		if (key != null && !key.isBlank()) {
			saved.put(envVar, key);
			save(saved);
			System.setProperty(springProp, key);
		}
	}

	private static String promptForKey(String provider, String envVar) {
		System.out.println("No API key found for provider '" + provider + "'.");
		System.out.println("Set " + envVar + " in your environment, or enter it now to save to " + CONFIG_FILE);
		System.out.print("Enter API key: ");
		System.out.flush();

		try {
			// Use console if available (hides input), else plain reader
			java.io.Console console = System.console();
			if (console != null) {
				char[] chars = console.readPassword();
				return chars != null ? new String(chars).strip() : null;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
				return reader.readLine();
			}
		}
		catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Parses a dotenv-style file ({@code KEY=value} or {@code export KEY=value} lines).
	 * Handles single and double quoted values. Silently returns empty map if unreadable.
	 */
	static Map<String, String> parseDotEnv(Path file) {
		Map<String, String> result = new LinkedHashMap<>();
		if (!Files.isRegularFile(file)) {
			return result;
		}
		try {
			for (String line : Files.readAllLines(file)) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				// Strip optional leading "export "
				if (line.startsWith("export ")) {
					line = line.substring(7).stripLeading();
				}
				int eq = line.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String k = line.substring(0, eq).strip();
				String v = line.substring(eq + 1).strip();
				// Strip surrounding quotes
				if (v.length() >= 2
						&& ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
					v = v.substring(1, v.length() - 1);
				}
				result.put(k, v);
			}
		}
		catch (IOException ex) {
			// Silently ignore
		}
		return result;
	}

	static Map<String, String> load() {
		Map<String, String> result = new LinkedHashMap<>();
		if (!Files.isRegularFile(CONFIG_FILE)) {
			return result;
		}
		try {
			for (String line : Files.readAllLines(CONFIG_FILE)) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int eq = line.indexOf('=');
				if (eq > 0) {
					result.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
				}
			}
		}
		catch (IOException ex) {
			// Silently ignore unreadable config
		}
		return result;
	}

	static void save(Map<String, String> entries) {
		try {
			Files.createDirectories(CONFIG_FILE.getParent());
			StringBuilder sb = new StringBuilder();
			sb.append("# Loopy API keys — do not share this file\n");
			for (Map.Entry<String, String> e : entries.entrySet()) {
				sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
			}
			Files.writeString(CONFIG_FILE, sb.toString());
			// Restrict permissions to owner only
			CONFIG_FILE.toFile().setReadable(false, false);
			CONFIG_FILE.toFile().setReadable(true, true);
			CONFIG_FILE.toFile().setWritable(false, false);
			CONFIG_FILE.toFile().setWritable(true, true);
		}
		catch (IOException ex) {
			System.err.println("Warning: could not save config: " + ex.getMessage());
		}
	}

}
