package com.naren.kadiri.chatdemo;


import org.springframework.ai.chat.client.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are a helpful assistant powered by Groq.")
                .build();
    }

    /**
     * Simple chat endpoint — send a message, get a response.
     * GET /api/chat/simple?message=Hello
     */
    @GetMapping("/simple")
    public Map<String, String> simpleChat(@RequestParam String message) {
        String response = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return Map.of(
                "question", message,
                "answer", response
        );
    }

    /**
     * POST endpoint with a JSON body.
     * POST /api/chat
     * Body: { "message": "Hello!" }
     */
    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String response = chatClient.prompt()
                .user(request.message())
                .call()
                .content();

        return Map.of(
                "question", request.message(),
                "answer", response
        );
    }

    /**
     * Streaming endpoint — returns response as a stream of text chunks.
     * GET /api/chat/stream?message=Tell me a joke
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamChat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    /**
     * Chat with a custom system prompt.
     * POST /api/chat/custom
     * Body: { "system": "You are a pirate", "message": "Hello!" }
     */
    @PostMapping("/custom")
    public Map<String, String> customChat(@RequestBody CustomChatRequest request) {
        String response = chatClient.prompt()
                .system(request.system())
                .user(request.message())
                .call()
                .content();

        return Map.of(
                "question", request.message(),
                "system", request.system(),
                "answer", response
        );
    }

    // --- Records for request bodies ---

    public record ChatRequest(String message) {}

    public record CustomChatRequest(String system, String message) {}
}