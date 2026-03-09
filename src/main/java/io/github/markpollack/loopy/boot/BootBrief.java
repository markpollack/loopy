package io.github.markpollack.loopy.boot;

/**
 * Captures the user's intent for a new Spring Boot project.
 *
 * <p>
 * Populated from CLI arguments, then enriched by {@code BootPreferences} before the
 * scaffold graph runs. All fields are required by the time the graph executes.
 * </p>
 *
 * @param name project name used for the output directory (e.g., {@code products-api})
 * @param groupId Maven groupId (e.g., {@code com.acme})
 * @param artifactId Maven artifactId (defaults to {@code name} if not specified)
 * @param packageName root Java package (e.g., {@code com.acme.productsapi})
 * @param template template name (e.g., {@code spring-boot-rest})
 * @param javaVersion Java major version (e.g., {@code 21})
 */
public record BootBrief(String name, String groupId, String artifactId, String packageName, String template,
		String javaVersion) {

	/** Template package replaced during scaffolding. */
	public static final String TEMPLATE_PACKAGE = "com.example.app";

	/** Template groupId replaced during scaffolding. */
	public static final String TEMPLATE_GROUP_ID = "com.example";

	/** Template artifactId replaced during scaffolding. */
	public static final String TEMPLATE_ARTIFACT_ID = "app";

}
