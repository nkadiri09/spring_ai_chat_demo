package com.naren.kadiri.chatdemo.langfuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spring AI Advisor that intercepts chat calls and sends traces to Langfuse.
 * This allows automatic tracing of all LLM calls.
 */
@Component
public class LangfuseAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(LangfuseAdvisor.class);
    private static final String ADVISOR_NAME = "LangfuseAdvisor";

    private final LangfuseService langfuseService;
    private final LangfuseProperties properties;

    public LangfuseAdvisor(LangfuseService langfuseService, LangfuseProperties properties) {
        this.langfuseService = langfuseService;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return ADVISOR_NAME;
    }

    @Override
    public int getOrder() {
        return 0; // Execute first in the advisor chain
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return chain.nextCall(request);
        }

        OffsetDateTime startTime = OffsetDateTime.now();

        // Create trace
        Map<String, Object> traceMetadata = buildTraceMetadata(request);
        String traceId = langfuseService.createTrace(
                "chat-completion",
                getUserId(request),
                traceMetadata
        );

        String errorMessage = null;
        String level = "DEFAULT";
        ChatClientResponse response = null;

        try {
            response = chain.nextCall(request);
            return response;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            level = "ERROR";
            throw e;
        } finally {
            OffsetDateTime endTime = OffsetDateTime.now();
            recordGeneration(traceId, request, response, startTime, endTime, level, errorMessage);
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            return chain.nextStream(request);
        }

        OffsetDateTime startTime = OffsetDateTime.now();

        Map<String, Object> traceMetadata = buildTraceMetadata(request);
        String traceId = langfuseService.createTrace(
                "chat-completion-stream",
                getUserId(request),
                traceMetadata
        );

        StringBuilder outputBuilder = new StringBuilder();
        final ChatResponse[] lastChatResponse = {null};

        return chain.nextStream(request)
                .doOnNext(response -> {
                    if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
                        String content = response.chatResponse().getResult().getOutput().getText();
                        if (content != null) {
                            outputBuilder.append(content);
                        }
                        lastChatResponse[0] = response.chatResponse();
                    }
                })
                .doOnComplete(() -> {
                    OffsetDateTime endTime = OffsetDateTime.now();
                    recordStreamGeneration(traceId, request, outputBuilder.toString(),
                            lastChatResponse[0], startTime, endTime, "DEFAULT", null);
                })
                .doOnError(error -> {
                    OffsetDateTime endTime = OffsetDateTime.now();
                    recordStreamGeneration(traceId, request, outputBuilder.toString(),
                            lastChatResponse[0], startTime, endTime, "ERROR", error.getMessage());
                });
    }

    private void recordGeneration(String traceId, ChatClientRequest request,
                                   ChatClientResponse response, OffsetDateTime startTime,
                                   OffsetDateTime endTime, String level, String errorMessage) {
        try {
            String model = extractModel(response);
            LangfuseService.UsageData usage = extractUsage(response);

            Object input = buildInput(request);
            Object output = response != null && response.chatResponse() != null
                    ? response.chatResponse().getResult().getOutput().getText()
                    : null;

            LangfuseService.GenerationData data = LangfuseService.GenerationData.builder()
                    .traceId(traceId)
                    .name("chat-generation")
                    .model(model)
                    .startTime(startTime)
                    .endTime(endTime)
                    .input(input)
                    .output(output)
                    .usage(usage)
                    .level(level)
                    .statusMessage(errorMessage)
                    .metadata(Map.of("advisor", ADVISOR_NAME))
                    .build();

            langfuseService.recordGeneration(data);
        } catch (Exception e) {
            logger.error("Failed to record generation to Langfuse", e);
        }
    }

    private void recordStreamGeneration(String traceId, ChatClientRequest request,
                                         String output, ChatResponse lastResponse,
                                         OffsetDateTime startTime, OffsetDateTime endTime,
                                         String level, String errorMessage) {
        try {
            String model = extractModelFromResponse(lastResponse);
            LangfuseService.UsageData usage = extractUsageFromResponse(lastResponse);

            Object input = buildInput(request);

            LangfuseService.GenerationData data = LangfuseService.GenerationData.builder()
                    .traceId(traceId)
                    .name("chat-generation-stream")
                    .model(model)
                    .startTime(startTime)
                    .endTime(endTime)
                    .input(input)
                    .output(output)
                    .usage(usage)
                    .level(level)
                    .statusMessage(errorMessage)
                    .metadata(Map.of("advisor", ADVISOR_NAME, "streaming", true))
                    .build();

            langfuseService.recordGeneration(data);
        } catch (Exception e) {
            logger.error("Failed to record stream generation to Langfuse", e);
        }
    }

    private Object buildInput(ChatClientRequest request) {
        Map<String, Object> input = new HashMap<>();

        Prompt prompt = request.prompt();
        if (prompt != null && prompt.getInstructions() != null) {
            input.put("messages", prompt.getInstructions().stream()
                    .map(m -> Map.of(
                            "role", m.getMessageType().name(),
                            "content", m.getText() != null ? m.getText() : ""
                    ))
                    .toList());
        }

        return input;
    }

    private Map<String, Object> buildTraceMetadata(ChatClientRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        Prompt prompt = request.prompt();
        metadata.put("hasPrompt", prompt != null);
        metadata.put("messageCount", prompt != null && prompt.getInstructions() != null
                ? prompt.getInstructions().size() : 0);
        return metadata;
    }

    private String getUserId(ChatClientRequest request) {
        Map<String, Object> context = request.context();
        if (context != null && context.containsKey("userId")) {
            return context.get("userId").toString();
        }
        return "anonymous";
    }

    private String extractModel(ChatClientResponse response) {
        if (response != null && response.chatResponse() != null
                && response.chatResponse().getMetadata() != null) {
            return response.chatResponse().getMetadata().getModel();
        }
        return "unknown";
    }

    private String extractModelFromResponse(ChatResponse response) {
        if (response != null && response.getMetadata() != null) {
            return response.getMetadata().getModel();
        }
        return "unknown";
    }

    private LangfuseService.UsageData extractUsage(ChatClientResponse response) {
        if (response != null && response.chatResponse() != null
                && response.chatResponse().getMetadata() != null
                && response.chatResponse().getMetadata().getUsage() != null) {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            return LangfuseService.UsageData.of(
                    (int) usage.getPromptTokens(),
                    (int) usage.getCompletionTokens(),
                    (int) usage.getTotalTokens()
            );
        }
        return null;
    }

    private LangfuseService.UsageData extractUsageFromResponse(ChatResponse response) {
        if (response != null && response.getMetadata() != null
                && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            return LangfuseService.UsageData.of(
                    (int) usage.getPromptTokens(),
                    (int) usage.getCompletionTokens(),
                    (int) usage.getTotalTokens()
            );
        }
        return null;
    }
}
