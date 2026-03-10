package io.github.markpollack.loopy.boot.modify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Classifies a natural-language modification intent to a {@link RecipeCatalog.Recipe}
 * using a single cheap LLM call (max 1 turn, no tools).
 *
 * <p>
 * The classifier maps free-form text to a recipe name and extracts any required
 * parameters (e.g., Java version number, Maven coordinates). When no recipe fits,
 * {@link ClassifierResult#matched()} returns {@code false}, allowing the caller to fall
 * through to a full agent.
 * </p>
 *
 * <p>
 * Uses {@code claude-haiku-4-5-20251001} as the classification model — cheap and fast for
 * single-turn structured output tasks.
 * </p>
 */
public class RecipeClassifier {

	private static final Logger logger = LoggerFactory.getLogger(RecipeClassifier.class);

	static final String CLASSIFICATION_MODEL = "claude-haiku-4-5-20251001";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ChatModel chatModel;

	public RecipeClassifier(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	public record ClassifierResult(@Nullable String recipeName, Map<String, String> params) {

		public boolean matched() {
			return recipeName != null && !"none".equalsIgnoreCase(recipeName);
		}

	}

	/**
	 * Classify the intent against the recipe catalog.
	 * @param intent natural-language intent string (e.g. "add native image support")
	 * @param projectContext brief description of current project state (java version, sql
	 * dialect, native)
	 * @return classifier result — check {@link ClassifierResult#matched()} before using
	 */
	public ClassifierResult classify(String intent, String projectContext) {
		String recipeList = RecipeCatalog.ALL.stream()
			.map(r -> "- " + r.name() + ": " + r.description()
					+ (r.paramNames().isEmpty() ? "" : " [extract params: " + String.join(", ", r.paramNames()) + "]"))
			.collect(Collectors.joining("\n"));

		String prompt = """
				Classify this Spring Boot project modification intent to a deterministic recipe.

				Available recipes:
				%s

				Current project state:
				%s

				User intent: "%s"

				Rules:
				- For SET_JAVA_VERSION: extract the version number as a string (e.g. "21", "17", "11").
				- For ADD_DEPENDENCY/REMOVE_DEPENDENCY: extract groupId and artifactId exactly.
				- Use "none" if the intent doesn't clearly map to any recipe, or requires multiple steps.

				Respond with JSON only — no explanation, no markdown fences:
				{"recipe":"RECIPE_NAME","params":{"key":"value"}}
				""".formatted(recipeList, projectContext, intent);

		try {
			var options = ChatOptions.builder().model(CLASSIFICATION_MODEL).build();
			var response = chatModel.call(new Prompt(prompt, options));
			String raw = response.getResult().getOutput().getText().trim();
			// Strip markdown code fences if model wrapped the JSON
			if (raw.startsWith("```")) {
				raw = raw.replaceAll("```[a-z]*\\n?", "").replaceAll("```", "").trim();
			}
			Map<String, Object> parsed = MAPPER.readValue(raw, new TypeReference<>() {
			});
			String recipe = String.valueOf(parsed.getOrDefault("recipe", "none"));
			Object paramsRaw = parsed.get("params");
			Map<String, String> params = (paramsRaw instanceof Map<?, ?> pm)
					? pm.entrySet()
						.stream()
						.filter(e -> e.getValue() != null)
						.collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue())))
					: Collections.emptyMap();
			logger.debug("Classified '{}' → recipe={}, params={}", intent, recipe, params);
			return new ClassifierResult(recipe, params);
		}
		catch (Exception ex) {
			logger.debug("Recipe classification failed, falling through to agent: {}", ex.getMessage());
			return new ClassifierResult("none", Collections.emptyMap());
		}
	}

}
