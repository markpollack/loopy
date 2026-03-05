package io.github.markpollack.loopy.forge;

import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import io.github.markpollack.loopy.command.CommandContext;
import io.github.markpollack.loopy.command.SlashCommand;

/**
 * Slash command that bootstraps a new agent experiment project from a YAML brief.
 *
 * <p>
 * Usage:
 * {@code /forge-agent --brief path/to/brief.yaml [--output /path/to/output] [--no-llm] [--no-kb]}
 */
public class ForgeAgentCommand implements SlashCommand {

	private static final Logger log = LoggerFactory.getLogger(ForgeAgentCommand.class);

	private static final String DEFAULT_TEMPLATE_REPO = "https://github.com/markpollack/agent-experiment-template.git";

	private static final String BRAVE_API_KEY_ENV = "BRAVE_API_KEY";

	private final TemplateCloner cloner;

	private final TemplateCustomizer customizer;

	private final String templateRepoUrl;

	@Nullable
	private final CustomizationPromptBuilder promptBuilder;

	@Nullable
	private final ChatModel chatModel;

	public ForgeAgentCommand() {
		this(new TemplateCloner(), new TemplateCustomizer(), DEFAULT_TEMPLATE_REPO, new CustomizationPromptBuilder(),
				null);
	}

	public ForgeAgentCommand(@Nullable ChatModel chatModel) {
		this(new TemplateCloner(), new TemplateCustomizer(), DEFAULT_TEMPLATE_REPO, new CustomizationPromptBuilder(),
				chatModel);
	}

	public ForgeAgentCommand(TemplateCloner cloner, TemplateCustomizer customizer, String templateRepoUrl,
			@Nullable CustomizationPromptBuilder promptBuilder, @Nullable ChatModel chatModel) {
		this.cloner = cloner;
		this.customizer = customizer;
		this.templateRepoUrl = templateRepoUrl;
		this.promptBuilder = promptBuilder;
		this.chatModel = chatModel;
	}

	@Override
	public String name() {
		return "forge-agent";
	}

	@Override
	public String description() {
		return "Bootstrap an agent experiment project from a brief";
	}

	@Override
	public boolean requiresArguments() {
		return true;
	}

	@Override
	public String execute(String args, CommandContext context) {
		if (args == null || args.isBlank()) {
			return "Usage: /forge-agent --brief <path> [--output <dir>] [--no-llm] [--no-kb]";
		}

		String briefPathStr = null;
		String outputDirStr = null;
		boolean noLlm = false;
		boolean noKb = false;

		String[] tokens = args.split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			if ("--brief".equals(tokens[i]) && i + 1 < tokens.length) {
				briefPathStr = tokens[++i];
			}
			else if ("--output".equals(tokens[i]) && i + 1 < tokens.length) {
				outputDirStr = tokens[++i];
			}
			else if ("--no-llm".equals(tokens[i])) {
				noLlm = true;
			}
			else if ("--no-kb".equals(tokens[i])) {
				noKb = true;
			}
		}

		if (briefPathStr == null) {
			return "Error: --brief <path> is required. "
					+ "Usage: /forge-agent --brief <path> [--output <dir>] [--no-llm] [--no-kb]";
		}

		try {
			Path briefPath = context.workingDirectory().resolve(briefPathStr);
			ExperimentBrief brief = ExperimentBrief.parse(briefPath);

			Path outputDir;
			if (outputDirStr != null) {
				outputDir = Path.of(outputDirStr);
				if (!outputDir.isAbsolute()) {
					outputDir = context.workingDirectory().resolve(outputDir);
				}
			}
			else {
				outputDir = context.workingDirectory().resolve(brief.name());
			}

			// Phase 1: Clone template
			cloner.cloneTemplate(this.templateRepoUrl, outputDir);

			// Phase 2: Deterministic customization
			customizer.customize(brief, outputDir);

			// Phase 3.5: KB bootstrapping via reference harvest (unless --no-kb)
			String kbMessage = runKBBootstrap(brief, outputDir, noKb);

			// Phase 3: LLM creative pass (unless --no-llm)
			if (!noLlm && promptBuilder != null) {
				String llmPrompt = promptBuilder.build(brief, outputDir.toString());
				return "Project scaffolded at: " + outputDir + kbMessage + "\n\n"
						+ "Run this LLM prompt in the project directory to generate domain-aware content:\n\n"
						+ llmPrompt;
			}

			return "Project scaffolded at: " + outputDir + kbMessage;
		}
		catch (Exception ex) {
			return "Error: " + ex.getMessage();
		}
	}

	/**
	 * Run the KB bootstrapping phase using a dedicated agent with web search and fetch
	 * tools.
	 * @return status message to append to the result
	 */
	private String runKBBootstrap(ExperimentBrief brief, Path outputDir, boolean noKb) {
		if (noKb) {
			return "";
		}

		if (chatModel == null) {
			log.debug("KB bootstrapping skipped: no ChatModel available");
			return "";
		}

		String braveApiKey = System.getenv(BRAVE_API_KEY_ENV);
		if (braveApiKey == null || braveApiKey.isBlank()) {
			log.warn("KB bootstrapping skipped: {} not set", BRAVE_API_KEY_ENV);
			return "\n(KB bootstrapping skipped: " + BRAVE_API_KEY_ENV + " not set)";
		}

		try {
			Path knowledgeDir = outputDir.resolve("knowledge");
			String knowledgeDirStr = knowledgeDir.toAbsolutePath().toString();

			KBBootstrapPromptBuilder kbPromptBuilder = new KBBootstrapPromptBuilder();
			String systemPrompt = kbPromptBuilder.buildSystemPrompt(brief, knowledgeDirStr);
			String userMessage = kbPromptBuilder.buildUserMessage(brief);

			// Build tools for KB scouting
			var braveSearch = BraveWebSearchTool.builder(braveApiKey).build();
			var chatClient = ChatClient.builder(chatModel).build();
			var smartFetch = SmartWebFetchTool.builder(chatClient).build();
			var fileTools = FileSystemTools.builder().build();

			ToolCallback[] tools = ToolCallbacks.from(braveSearch, smartFetch, fileTools);

			// Build a lightweight agent loop using Spring AI's built-in ToolCallAdvisor
			var toolCallingManager = DefaultToolCallingManager.builder().build();
			var advisor = ToolCallAdvisor.builder().toolCallingManager(toolCallingManager).build();

			var kbClient = ChatClient.builder(chatModel)
				.defaultAdvisors(advisor)
				.defaultToolCallbacks(tools)
				.defaultToolContext(Map.of("agentId", "kb-scout"))
				.build();

			log.info("Starting KB bootstrapping for domain: {}", brief.domainName());
			kbClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
			log.info("KB bootstrapping completed for: {}", brief.name());

			return "\n(KB bootstrapped with web references)";
		}
		catch (Exception ex) {
			log.warn("KB bootstrapping failed (non-fatal): {}", ex.getMessage());
			return "\n(KB bootstrapping failed: " + ex.getMessage() + ")";
		}
	}

}
