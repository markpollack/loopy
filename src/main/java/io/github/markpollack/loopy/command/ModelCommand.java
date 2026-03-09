package io.github.markpollack.loopy.command;

import java.util.List;
import java.util.Map;

/**
 * Slash command for listing and switching models within the active provider.
 * <p>
 * Usage: {@code /model} — list available models with their index Usage:
 * {@code /model <number>} — switch by index Usage: {@code /model <name>} — switch by
 * exact or prefix match (or any custom name)
 */
public class ModelCommand implements SlashCommand {

	private static final Map<String, List<String>> MODELS_BY_PROVIDER = Map.of("anthropic",
			List.of("claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5-20251001"), "openai",
			List.of("gpt-4.1", "gpt-4o", "gpt-4o-mini", "o4-mini"), "google-genai",
			List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"));

	private final String provider;

	private String activeModel;

	public ModelCommand(String provider, String initialModel) {
		this.provider = provider;
		this.activeModel = initialModel;
	}

	/** Returns the currently active model name, or null if using the provider default. */
	public String getActiveModel() {
		return activeModel;
	}

	@Override
	public String name() {
		return "model";
	}

	@Override
	public String description() {
		return "List or switch model (within current provider: " + provider + ")";
	}

	@Override
	public String execute(String args, CommandContext context) {
		List<String> models = MODELS_BY_PROVIDER.getOrDefault(provider, List.of());

		if (args == null || args.isBlank()) {
			// List available models
			StringBuilder sb = new StringBuilder("Provider: ").append(provider).append("\n");
			for (int i = 0; i < models.size(); i++) {
				String m = models.get(i);
				String marker = m.equals(activeModel) ? " ◀ active" : "";
				sb.append("  ").append(i + 1).append(". ").append(m).append(marker).append("\n");
			}
			if (activeModel != null && !models.contains(activeModel)) {
				sb.append("  (custom) ").append(activeModel).append(" ◀ active\n");
			}
			sb.append("\nUsage: /model <number or name>");
			return sb.toString().trim();
		}

		String arg = args.trim();

		// Try numeric index
		try {
			int idx = Integer.parseInt(arg) - 1;
			if (idx >= 0 && idx < models.size()) {
				String selected = models.get(idx);
				activeModel = selected;
				context.setModel().accept(selected);
				return "Switched to " + selected;
			}
			return "No model at index " + (idx + 1) + ". Run /model to list options.";
		}
		catch (NumberFormatException ex) {
			// fall through to name matching
		}

		// Exact or prefix match against known models
		for (String m : models) {
			if (m.equals(arg) || m.startsWith(arg)) {
				activeModel = m;
				context.setModel().accept(m);
				return "Switched to " + m;
			}
		}

		// Accept any custom model name (for local endpoints, preview models, etc.)
		activeModel = arg;
		context.setModel().accept(arg);
		return "Switched to " + arg + " (custom — not in the known list for " + provider + ")";
	}

}
