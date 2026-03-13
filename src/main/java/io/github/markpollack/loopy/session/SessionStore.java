package io.github.markpollack.loopy.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persists conversation sessions as JSON files in {@code ~/.config/loopy/sessions/}.
 * <p>
 * Each session file is named {@code {timestamp}-{first-4-words}.json}. The file contains
 * message history (user, assistant, tool) plus metadata (model, working directory).
 * System messages are excluded — they are regenerated from config on each run.
 */
public class SessionStore {

	private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

	private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
		.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	private final Path sessionsDir;

	public SessionStore() {
		this(defaultSessionsDir());
	}

	public SessionStore(Path sessionsDir) {
		this.sessionsDir = sessionsDir;
	}

	static Path defaultSessionsDir() {
		return Path.of(System.getProperty("user.home"), ".config", "loopy", "sessions");
	}

	/**
	 * Saves the current conversation to a session file.
	 * @param memory the chat memory to read messages from
	 * @param conversationId the conversation ID in the memory
	 * @param metadata session metadata (model, workingDirectory, provider)
	 * @param name optional user-supplied name; auto-generated from first message if null
	 * @return the session ID (filename without .json)
	 */
	public String save(ChatMemory memory, String conversationId, SessionMetadata metadata, @Nullable String name) {
		List<Message> messages = memory.get(conversationId);
		return save(messages, metadata, name);
	}

	/**
	 * Saves a list of messages to a session file.
	 * @return the session ID
	 */
	public String save(List<Message> messages, SessionMetadata metadata, @Nullable String name) {
		try {
			Files.createDirectories(sessionsDir);
		}
		catch (IOException ex) {
			throw new RuntimeException("Cannot create sessions directory: " + sessionsDir, ex);
		}

		String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
		String slug = name != null ? slugify(name) : autoSlug(messages);
		String id = timestamp + (slug.isEmpty() ? "" : "-" + slug);
		Path file = sessionsDir.resolve(id + ".json");

		var stored = new StoredSession(id, LocalDateTime.now().toString(), metadata.workingDirectory(),
				metadata.model(), metadata.provider(), toStored(messages));
		try {
			MAPPER.writeValue(file.toFile(), stored);
			log.debug("Session saved: {}", file);
			return id;
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to save session: " + file, ex);
		}
	}

	/**
	 * Loads a session by ID prefix (most recent match if ambiguous).
	 * @return messages from the session, or empty if not found
	 */
	public Optional<List<Message>> load(String idPrefix) {
		try {
			if (!Files.isDirectory(sessionsDir)) {
				return Optional.empty();
			}
			var matching = Files.list(sessionsDir)
				.filter(p -> p.getFileName().toString().startsWith(idPrefix) && p.toString().endsWith(".json"))
				.sorted(Comparator.reverseOrder())
				.toList();
			if (matching.isEmpty()) {
				return Optional.empty();
			}
			StoredSession session = MAPPER.readValue(matching.get(0).toFile(), StoredSession.class);
			return Optional.of(fromStored(session.messages()));
		}
		catch (IOException ex) {
			log.warn("Failed to load session '{}': {}", idPrefix, ex.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Lists all saved sessions, most recent first.
	 */
	public List<SessionSummary> list() {
		if (!Files.isDirectory(sessionsDir)) {
			return List.of();
		}
		try {
			return Files.list(sessionsDir)
				.filter(p -> p.toString().endsWith(".json"))
				.sorted(Comparator.reverseOrder())
				.map(this::summarize)
				.filter(s -> s != null)
				.collect(Collectors.toList());
		}
		catch (IOException ex) {
			log.warn("Failed to list sessions: {}", ex.getMessage());
			return List.of();
		}
	}

	private @Nullable SessionSummary summarize(Path file) {
		try {
			StoredSession s = MAPPER.readValue(file.toFile(), StoredSession.class);
			String first = s.messages()
				.stream()
				.filter(m -> "user".equals(m.role()))
				.map(StoredMessage::text)
				.filter(t -> t != null && !t.isBlank())
				.findFirst()
				.map(t -> t.length() > 60 ? t.substring(0, 57) + "..." : t)
				.orElse("(no user messages)");
			return new SessionSummary(s.id(), s.createdAt(), s.workingDirectory(), first);
		}
		catch (IOException ex) {
			log.warn("Skipping unreadable session file {}: {}", file.getFileName(), ex.getMessage());
			return null;
		}
	}

	// --- Conversion: Spring AI messages ↔ StoredMessage ---

	private List<StoredMessage> toStored(List<Message> messages) {
		return messages.stream().map(this::toStored).filter(m -> m != null).toList();
	}

	private @Nullable StoredMessage toStored(Message message) {
		MessageType type = message.getMessageType();
		return switch (type) {
			case USER -> new StoredMessage("user", message.getText(), null, null);
			case ASSISTANT -> {
				var am = (AssistantMessage) message;
				List<StoredToolCall> calls = null;
				if (am.hasToolCalls()) {
					calls = am.getToolCalls()
						.stream()
						.map(tc -> new StoredToolCall(tc.id(), tc.type(), tc.name(), tc.arguments()))
						.toList();
				}
				yield new StoredMessage("assistant", am.getText(), calls, null);
			}
			case TOOL -> {
				var trm = (ToolResponseMessage) message;
				List<StoredToolResponse> responses = trm.getResponses()
					.stream()
					.map(r -> new StoredToolResponse(r.id(), r.name(), r.responseData()))
					.toList();
				yield new StoredMessage("tool", null, null, responses);
			}
			case SYSTEM -> null; // system messages are excluded — regenerated from config
		};
	}

	private List<Message> fromStored(List<StoredMessage> stored) {
		var messages = new ArrayList<Message>();
		for (var m : stored) {
			Message msg = fromStored(m);
			if (msg != null) {
				messages.add(msg);
			}
		}
		return messages;
	}

	private @Nullable Message fromStored(StoredMessage m) {
		return switch (m.role()) {
			case "user" -> new UserMessage(m.text() != null ? m.text() : "");
			case "assistant" -> {
				var builder = AssistantMessage.builder();
				if (m.text() != null) {
					builder.content(m.text());
				}
				if (m.toolCalls() != null) {
					builder.toolCalls(m.toolCalls()
						.stream()
						.map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), tc.arguments()))
						.toList());
				}
				yield builder.build();
			}
			case "tool" -> {
				if (m.responses() == null) {
					yield null;
				}
				List<ToolResponseMessage.ToolResponse> responses = m.responses()
					.stream()
					.map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData()))
					.toList();
				yield ToolResponseMessage.builder().responses(responses).build();
			}
			default -> null;
		};
	}

	// --- Utility ---

	private String autoSlug(List<Message> messages) {
		String first = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(Message::getText)
			.filter(t -> t != null && !t.isBlank())
			.findFirst()
			.orElse("");
		return slugify(first);
	}

	static String slugify(String text) {
		String[] words = text.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
		return Arrays.stream(words).filter(w -> !w.isBlank()).limit(4).collect(Collectors.joining("-"));
	}

	// --- Stored record types (JSON schema) ---

	record StoredSession(String id, String createdAt, String workingDirectory, String model, String provider,
			List<StoredMessage> messages) {
	}

	record StoredMessage(String role, @Nullable String text, @Nullable List<StoredToolCall> toolCalls,
			@Nullable List<StoredToolResponse> responses) {
	}

	record StoredToolCall(String id, String type, String name, String arguments) {
	}

	record StoredToolResponse(String id, String name, String responseData) {
	}

	// --- Public types ---

	/**
	 * Metadata written alongside the message history.
	 */
	public record SessionMetadata(String workingDirectory, String model, String provider) {
	}

	/**
	 * Lightweight summary for listing sessions.
	 */
	public record SessionSummary(String id, String createdAt, String workingDirectory, String firstMessage) {
	}

}
