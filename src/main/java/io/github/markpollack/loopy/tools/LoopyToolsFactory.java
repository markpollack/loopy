package io.github.markpollack.loopy.tools;

import io.github.markpollack.loopy.agent.BashTool;
import io.github.markpollack.loopy.boot.BootModifyTool;
import io.github.markpollack.loopy.boot.BootNewTool;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ListDirectoryTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Creates {@link ToolCallback} lists for named tool profiles (DD-12, DD-16).
 * <p>
 * Built-in profiles:
 * <ul>
 * <li>{@code dev} — bash, file-system, grep, glob, list-directory, todo-write,
 * ask-user-question (when interactive), brave-search (when BRAVE_API_KEY set)</li>
 * <li>{@code boot} — Spring Boot scaffolding tools (boot-new, boot-modify)</li>
 * <li>{@code headless} — same as dev, minus ask-user-question (for CI/CD)</li>
 * <li>{@code readonly} — read-only: file-system, grep, glob, list-directory only</li>
 * </ul>
 * <p>
 * Custom profiles are supported via {@link ToolProfileContributor} — implement the
 * interface and register via {@code META-INF/services} (Java {@link ServiceLoader}). The
 * profile name declared in {@code agent.yaml} must match
 * {@link ToolProfileContributor#profileName()}.
 */
@NullMarked
public class LoopyToolsFactory {

	private static final Logger log = LoggerFactory.getLogger(LoopyToolsFactory.class);

	/**
	 * Returns the combined list of tool callbacks for the given profiles, in declaration
	 * order. Unknown profile names (no built-in, no contributor) are logged and skipped.
	 */
	public static List<ToolCallback> toolsForProfiles(List<String> profiles, ToolFactoryContext ctx) {
		List<ToolProfileContributor> contributors = ServiceLoader.load(ToolProfileContributor.class)
			.stream()
			.map(ServiceLoader.Provider::get)
			.toList();
		return toolsForProfiles(profiles, ctx, contributors);
	}

	/**
	 * Package-private overload — accepts an explicit contributor list for testing.
	 */
	static List<ToolCallback> toolsForProfiles(List<String> profiles, ToolFactoryContext ctx,
			List<ToolProfileContributor> contributors) {
		List<ToolCallback> result = new ArrayList<>();
		for (String profile : profiles) {
			List<ToolCallback> bundle = bundleFor(profile, ctx, contributors);
			if (bundle.isEmpty() && !isKnownProfile(profile)) {
				log.warn(
						"No tools found for profile '{}' — skipping. Built-in profiles: dev, boot, headless, readonly. "
								+ "Custom profiles require a ToolProfileContributor registered via META-INF/services.",
						profile);
			}
			result.addAll(bundle);
		}
		return List.copyOf(result);
	}

	private static boolean isKnownProfile(String profile) {
		return switch (profile) {
			case "dev", "boot", "headless", "readonly" -> true;
			default -> false;
		};
	}

	private static List<ToolCallback> bundleFor(String profile, ToolFactoryContext ctx,
			List<ToolProfileContributor> contributors) {
		return switch (profile) {
			case "dev" -> devTools(ctx);
			case "boot" -> bootTools(ctx);
			case "headless" -> headlessTools(ctx);
			case "readonly" -> readonlyTools(ctx);
			default -> contributors.stream()
				.filter(c -> c.profileName().equals(profile))
				.findFirst()
				.map(c -> c.tools(ctx))
				.orElse(List.of());
		};
	}

	/**
	 * Dev profile: full interactive toolset including bash, file-system, search, and
	 * optional user-question + brave-search when credentials are available.
	 */
	private static List<ToolCallback> devTools(ToolFactoryContext ctx) {
		List<Object> annotated = new ArrayList<>();
		annotated.add(FileSystemTools.builder().build());
		annotated.add(new BashTool(ctx.workingDirectory(), ctx.commandTimeout()));
		annotated.add(GlobTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(GrepTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(ListDirectoryTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(TodoWriteTool.builder().build());

		if (ctx.interactive() && ctx.agentCallback() != null) {
			annotated.add(AskUserQuestionTool.builder()
				.questionHandler(questions -> ctx.agentCallback().onQuestion(questions))
				.build());
		}

		String braveApiKey = System.getenv("BRAVE_API_KEY");
		if (braveApiKey != null && !braveApiKey.isBlank()) {
			annotated.add(BraveWebSearchTool.builder(braveApiKey).build());
			annotated.add(SmartWebFetchTool.builder(ChatClient.builder(ctx.chatModel()).build()).build());
		}

		return Arrays.asList(ToolCallbacks.from(annotated.toArray()));
	}

	/**
	 * Boot profile: Spring Boot scaffolding tools. Only active when Loopy is used as a
	 * Spring Boot development assistant.
	 */
	private static List<ToolCallback> bootTools(ToolFactoryContext ctx) {
		List<Object> annotated = new ArrayList<>();
		annotated.add(new BootNewTool(ctx.workingDirectory(), ctx.chatModel()));
		annotated.add(new BootModifyTool(ctx.workingDirectory()));
		return Arrays.asList(ToolCallbacks.from(annotated.toArray()));
	}

	/**
	 * Headless profile: same as dev but without AskUserQuestion. For CI/CD batch agents
	 * where no human is available to answer questions.
	 */
	private static List<ToolCallback> headlessTools(ToolFactoryContext ctx) {
		List<Object> annotated = new ArrayList<>();
		annotated.add(FileSystemTools.builder().build());
		annotated.add(new BashTool(ctx.workingDirectory(), ctx.commandTimeout()));
		annotated.add(GlobTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(GrepTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(ListDirectoryTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(TodoWriteTool.builder().build());

		String braveApiKey = System.getenv("BRAVE_API_KEY");
		if (braveApiKey != null && !braveApiKey.isBlank()) {
			annotated.add(BraveWebSearchTool.builder(braveApiKey).build());
			annotated.add(SmartWebFetchTool.builder(ChatClient.builder(ctx.chatModel()).build()).build());
		}

		return Arrays.asList(ToolCallbacks.from(annotated.toArray()));
	}

	/**
	 * Readonly profile: read-only access only. No bash, no file writes. For secure
	 * production environments where shell execution is not permitted.
	 */
	private static List<ToolCallback> readonlyTools(ToolFactoryContext ctx) {
		List<Object> annotated = new ArrayList<>();
		annotated.add(FileSystemTools.builder().build());
		annotated.add(GlobTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(GrepTool.builder().workingDirectory(ctx.workingDirectory()).build());
		annotated.add(ListDirectoryTool.builder().workingDirectory(ctx.workingDirectory()).build());
		return Arrays.asList(ToolCallbacks.from(annotated.toArray()));
	}

}
