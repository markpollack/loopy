package com.example.app;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Simple wrapper around Spring AI's ChatClient.
 *
 * <p>
 * Add tools, advisors, and richer prompts here as your application evolves.
 * </p>
 */
@Service
public class AssistantService {

    private final ChatClient chatClient;

    public AssistantService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant.")
            .build();
    }

    public String chat(String message) {
        return chatClient.prompt(message).call().content();
    }

}
