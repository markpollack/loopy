package io.github.markpollack.loopy;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import picocli.CommandLine;

@SpringBootApplication
public class LoopyApp implements CommandLineRunner {

	private final ObjectProvider<ChatModel> chatModelProvider;

	public LoopyApp(ObjectProvider<ChatModel> chatModelProvider) {
		this.chatModelProvider = chatModelProvider;
	}

	@Override
	public void run(String... args) {
		int exitCode = new CommandLine(new LoopyCommand(this.chatModelProvider.getIfAvailable())).execute(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	public static void main(String[] args) {
		// Parse CLI flags that affect Spring context before it boots.
		// All three provider starters have matchIfMissing=true — explicit
		// spring.ai.model.chat prevents multiple ChatModel beans.
		String provider = extractFlag(args, "--provider", "anthropic");
		System.setProperty("spring.ai.model.chat", provider);

		String baseUrl = extractFlag(args, "--base-url", null);
		if (baseUrl != null) {
			System.setProperty("spring.ai." + provider + ".base-url", baseUrl);
		}

		if (hasFlag(args, "--debug")) {
			System.setProperty("logging.level.io.github.markpollack.loopy", "DEBUG");
			System.setProperty("logging.level.root", "WARN");
		}

		// Ensure API key is available before Spring AI auto-configures the ChatModel
		LoopyConfig.ensureApiKey(provider);

		// Hint about other available providers (shown once at startup)
		printProviderNotice(provider);

		SpringApplication.run(LoopyApp.class, args);
	}

	private static String extractFlag(String[] args, String name, String defaultValue) {
		for (int i = 0; i < args.length; i++) {
			if (name.equals(args[i]) && i + 1 < args.length) {
				return args[i + 1];
			}
			if (args[i].startsWith(name + "=")) {
				return args[i].substring(name.length() + 1);
			}
		}
		return defaultValue;
	}

	private static void printProviderNotice(String activeProvider) {
		java.util.List<String> others = new java.util.ArrayList<>();
		for (String p : java.util.List.of("anthropic", "openai", "google-genai")) {
			if (!p.equals(activeProvider) && LoopyConfig.hasKey(p)) {
				others.add(p + " (" + LoopyConfig.envVarFor(p) + ")");
			}
		}
		if (!others.isEmpty()) {
			System.err.println("Using " + activeProvider + "  •  Also found: " + String.join(", ", others)
					+ "  (--provider to switch)");
		}
	}

	private static boolean hasFlag(String[] args, String name) {
		for (String arg : args) {
			if (name.equals(arg)) {
				return true;
			}
		}
		return false;
	}

}
