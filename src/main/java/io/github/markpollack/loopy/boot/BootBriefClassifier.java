package io.github.markpollack.loopy.boot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

/**
 * Classifies a natural-language {@code /boot-new} description into structured parameters
 * using a single cheap LLM call (Haiku).
 *
 * <p>
 * Invoked when the user types something like:
 * </p>
 *
 * <pre>
 * /boot-new a REST API called orders-service for com.acme using Java 21
 * </pre>
 *
 * <p>
 * instead of the structured flag form. Extracts {@code name}, {@code group},
 * {@code template}, and {@code javaVersion}. When the name cannot be determined,
 * {@link ClassifiedBrief#hasName()} returns {@code false} and the caller falls back to
 * showing help text.
 * </p>
 */
public class BootBriefClassifier {

	private static final Logger logger = LoggerFactory.getLogger(BootBriefClassifier.class);

	static final String CLASSIFICATION_MODEL = "claude-haiku-4-5-20251001";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ChatModel chatModel;

	public BootBriefClassifier(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Structured result from NL classification.
	 *
	 * @param name kebab-case artifact name (null if not determinable)
	 * @param group Maven groupId (null if not mentioned)
	 * @param template template name (null means use default)
	 * @param javaVersion Java major version string (null if not mentioned)
	 */
	public record ClassifiedBrief(@Nullable String name, @Nullable String group, @Nullable String template,
			@Nullable String javaVersion) {

		public boolean hasName() {
			return name != null && !name.isBlank();
		}

	}

	/**
	 * Extract brief parameters from a natural-language description.
	 * @param input free-form args string, e.g.
	 * {@code "REST API for com.acme using JDK 21"}
	 * @return classified result — check {@link ClassifiedBrief#hasName()} before using
	 */
	public ClassifiedBrief classify(String input) {
		String prompt = """
				Extract Spring Boot project parameters from this natural-language description.

				Description: "%s"

				Available templates:
				- spring-boot-minimal: basic project, no web layer
				- spring-boot-rest: REST API with Spring MVC
				- spring-boot-jpa: REST API + JPA/Hibernate + H2
				- spring-ai-app: Spring AI chat/LLM application

				Extraction rules:
				- name: A concise kebab-case artifact slug derived from the description
				  (e.g. "orders-service", "products-api", "my-rest-app"). Required.
				  If the user gives a name explicitly, use it. Otherwise infer one.
				- group: Maven groupId / base package (e.g. "com.acme", "org.example").
				  Extract only if clearly stated. Null otherwise.
				- template: pick the closest match. Use "spring-boot-rest" for REST/API hints,
				  "spring-boot-jpa" for database/JPA hints, "spring-ai-app" for AI/LLM hints,
				  "spring-boot-minimal" otherwise.
				- javaVersion: Java major version as a plain string: "21", "17", "11", etc.
				  Null if not mentioned. Treat "JDK 21", "Java 21", "jdk21" all as "21".

				Respond with JSON only — no explanation, no markdown code fences:
				{"name":"...","group":"...","template":"...","javaVersion":"..."}
				Use JSON null (not the string "null") for fields that cannot be determined.
				""".formatted(input);

		try {
			var options = ChatOptions.builder().model(CLASSIFICATION_MODEL).build();
			var response = chatModel.call(new Prompt(prompt, options));
			String raw = response.getResult().getOutput().getText().trim();
			if (raw.startsWith("```")) {
				raw = raw.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
			}
			Map<String, Object> parsed = MAPPER.readValue(raw, new TypeReference<>() {
			});
			String name = nullIfBlankOrNull(parsed.get("name"));
			String group = nullIfBlankOrNull(parsed.get("group"));
			String template = nullIfBlankOrNull(parsed.get("template"));
			String javaVersion = nullIfBlankOrNull(parsed.get("javaVersion"));
			logger.debug("Classified '{}' → name={}, group={}, template={}, javaVersion={}", input, name, group,
					template, javaVersion);
			return new ClassifiedBrief(name, group, template, javaVersion);
		}
		catch (Exception ex) {
			logger.debug("Brief classification failed: {}", ex.getMessage());
			return new ClassifiedBrief(null, null, null, null);
		}
	}

	private static @Nullable String nullIfBlankOrNull(@Nullable Object o) {
		if (o == null) {
			return null;
		}
		String s = String.valueOf(o).trim();
		return (s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
	}

}
