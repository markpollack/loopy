package io.github.markpollack.loopy.boot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Classifies a natural-language dependency description into Maven
 * {@code groupId:artifactId} coordinates using a single cheap LLM call (Haiku).
 *
 * <p>
 * Used by {@code /boot-setup --more} to resolve free-form text like
 * {@code "redis and flyway"} into
 * {@code ["org.springframework.boot:spring-boot-starter-data-redis",
 * "org.flywaydb:flyway-core"]}.
 * </p>
 */
public class BootDependencyClassifier {

	private static final Logger logger = LoggerFactory.getLogger(BootDependencyClassifier.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ChatModel chatModel;

	public BootDependencyClassifier(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Resolve a free-form dependency description to Maven coordinates.
	 * @param input e.g. {@code "redis and flyway"} or
	 * {@code "I always use prometheus and oauth2"}
	 * @return list of {@code "groupId:artifactId"} strings (empty if nothing matched)
	 */
	public List<String> classify(String input) {
		String prompt = """
				Map this natural-language description to Spring Boot Maven coordinates.

				Input: "%s"

				Common Spring Boot dependency coordinates (groupId:artifactId):
				- org.springframework.boot:spring-boot-starter-data-redis
				- org.springframework.boot:spring-boot-starter-data-jpa
				- org.springframework.boot:spring-boot-starter-security
				- org.springframework.boot:spring-boot-starter-validation
				- org.springframework.boot:spring-boot-starter-mail
				- org.springframework.boot:spring-boot-starter-cache
				- org.springframework.boot:spring-boot-starter-aop
				- org.springframework.boot:spring-boot-starter-webflux
				- org.springframework.boot:spring-boot-starter-oauth2-client
				- org.springframework.boot:spring-boot-starter-oauth2-resource-server
				- org.springframework.boot:spring-boot-starter-amqp
				- org.springframework.boot:spring-boot-starter-artemis
				- org.springframework.kafka:spring-kafka
				- org.flywaydb:flyway-core
				- org.liquibase:liquibase-core
				- io.micrometer:micrometer-registry-prometheus
				- org.projectlombok:lombok

				Return a JSON array of "groupId:artifactId" strings only.
				No explanation, no markdown code fences.
				Example: ["org.springframework.boot:spring-boot-starter-data-redis","org.flywaydb:flyway-core"]
				If nothing matches, return: []
				""".formatted(input);

		try {
			var options = ChatOptions.builder().model(BootBriefClassifier.CLASSIFICATION_MODEL).build();
			var response = chatModel.call(new Prompt(prompt, options));
			String raw = response.getResult().getOutput().getText().trim();
			if (raw.startsWith("```")) {
				raw = raw.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
			}
			List<String> result = MAPPER.readValue(raw, new TypeReference<>() {
			});
			logger.debug("Classified '{}' → {}", input, result);
			return result;
		}
		catch (Exception ex) {
			logger.debug("Dependency classification failed: {}", ex.getMessage());
			return List.of();
		}
	}

}
