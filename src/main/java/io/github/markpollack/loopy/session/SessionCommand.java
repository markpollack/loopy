package io.github.markpollack.loopy.session;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Slash command for session persistence.
 * <p>
 * Subcommands:
 * <ul>
 * <li>{@code /session save [name]} — save current session, auto-named if no name</li>
 * <li>{@code /session list} — list saved sessions (most recent first)</li>
 * <li>{@code /session load <id>} — restore a previous session into current memory</li>
 * </ul>
 */
public class SessionCommand implements SlashCommand {

	private final ChatMemory memory;

	private final String conversationId;

	private final SessionStore store;

	private final String model;

	private final String provider;

	public SessionCommand(ChatMemory memory, String conversationId, SessionStore store, String model, String provider) {
		this.memory = memory;
		this.conversationId = conversationId;
		this.store = store;
		this.model = model;
		this.provider = provider;
	}

	@Override
	public String name() {
		return "session";
	}

	@Override
	public String description() {
		return "Save, list, or load sessions: /session save [name] | list | load <id>";
	}

	@Override
	public boolean requiresArguments() {
		return true;
	}

	@Override
	public String execute(String args, CommandContext context) {
		String[] parts = args.trim().split("\\s+", 2);
		String sub = parts[0].toLowerCase();
		String rest = parts.length > 1 ? parts[1].trim() : "";

		return switch (sub) {
			case "save" -> doSave(rest.isEmpty() ? null : rest, context);
			case "list" -> doList();
			case "load" -> doLoad(rest, context);
			default -> "Unknown subcommand. Usage: /session save [name] | list | load <id>";
		};
	}

	private String doSave(@org.jspecify.annotations.Nullable String name, CommandContext context) {
		List<Message> messages = memory.get(conversationId);
		if (messages.isEmpty()) {
			return "Nothing to save — session is empty.";
		}
		String workDir = context.workingDirectory().toAbsolutePath().toString();
		var metadata = new SessionStore.SessionMetadata(workDir, model, provider);
		String id = store.save(messages, metadata, name);
		return "Session saved: " + id;
	}

	private String doList() {
		List<SessionStore.SessionSummary> sessions = store.list();
		if (sessions.isEmpty()) {
			return "No saved sessions.";
		}
		var sb = new StringBuilder("Saved sessions (most recent first):\n");
		for (var s : sessions) {
			sb.append("  ").append(s.id()).append("\n");
			sb.append("    ").append(s.createdAt()).append("  ").append(s.workingDirectory()).append("\n");
			sb.append("    ").append(s.firstMessage()).append("\n");
		}
		return sb.toString().stripTrailing();
	}

	private String doLoad(String id, CommandContext context) {
		if (id.isBlank()) {
			return "Usage: /session load <id>";
		}
		var result = store.load(id);
		if (result.isEmpty()) {
			return "Session not found: " + id;
		}
		List<Message> loaded = result.get();
		memory.clear(conversationId);
		memory.add(conversationId, loaded);
		return "Session loaded: " + id + " (" + loaded.size() + " messages)";
	}

}
