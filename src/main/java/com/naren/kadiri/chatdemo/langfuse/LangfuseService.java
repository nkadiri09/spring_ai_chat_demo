package com.naren.kadiri.chatdemo.langfuse;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.LangfuseClientBuilder;
import com.langfuse.client.resources.ingestion.requests.IngestionRequest;
import com.langfuse.client.resources.ingestion.types.*;
import com.langfuse.client.resources.commons.types.Usage;
import com.langfuse.client.resources.commons.types.ObservationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for sending traces to Langfuse using the official Java SDK.
 */
@Service
public class LangfuseService {

    private static final Logger logger = LoggerFactory.getLogger(LangfuseService.class);

    private final LangfuseProperties properties;
    private final LangfuseClient client;

    public LangfuseService(LangfuseProperties properties) {
        this.properties = properties;

        if (properties.isConfigured()) {
            this.client = new LangfuseClientBuilder()
                    .url(properties.getHost())
                    .credentials(properties.getPublicKey(), properties.getSecretKey())
                    .build();
            logger.info("Langfuse client initialized with host: {}", properties.getHost());
        } else {
            this.client = null;
            logger.warn("Langfuse is not configured. Set langfuse.public-key and langfuse.secret-key");
        }
    }

    /**
     * Creates a new trace and returns its ID.
     */
    public String createTrace(String name, String userId, Map<String, Object> metadata) {
        String traceId = UUID.randomUUID().toString();

        if (!isEnabled()) {
            return traceId;
        }

        CompletableFuture.runAsync(() -> {
            try {
                TraceBody traceBody = TraceBody.builder()
                        .id(traceId)
                        .name(name)
                        .userId(userId)
                        .metadata(metadata)
                        .timestamp(OffsetDateTime.now())
                        .build();

                IngestionEvent event = IngestionEvent.traceCreate(
                        TraceEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .timestamp(OffsetDateTime.now().toString())
                                .body(traceBody)
                                .build()
                );

                IngestionRequest request = IngestionRequest.builder()
                        .batch(List.of(event))
                        .build();

                client.ingestion().batch(request);
                logger.debug("Trace created: {}", traceId);
            } catch (Exception e) {
                logger.error("Failed to create trace in Langfuse", e);
            }
        });

        return traceId;
    }

    /**
     * Records a generation (LLM call) within a trace.
     */
    public void recordGeneration(GenerationData data) {
        if (!isEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                CreateGenerationBody.Builder bodyBuilder = CreateGenerationBody.builder()
                        .id(data.generationId())
                        .traceId(data.traceId())
                        .name(data.name())
                        .model(data.model())
                        .startTime(data.startTime())
                        .endTime(data.endTime())
                        .input(data.input())
                        .output(data.output())
                        .metadata(data.metadata());

                if (data.usage() != null) {
                    bodyBuilder.usage(IngestionUsage.of(
                            Usage.builder()
                                    .input(data.usage().promptTokens())
                                    .output(data.usage().completionTokens())
                                    .total(data.usage().totalTokens())
                                    .build()
                    ));
                }

                if (data.level() != null) {
                    bodyBuilder.level(ObservationLevel.valueOf(data.level()));
                }

                if (data.statusMessage() != null) {
                    bodyBuilder.statusMessage(data.statusMessage());
                }

                IngestionEvent event = IngestionEvent.generationCreate(
                        CreateGenerationEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .timestamp(OffsetDateTime.now().toString())
                                .body(bodyBuilder.build())
                                .build()
                );

                IngestionRequest request = IngestionRequest.builder()
                        .batch(List.of(event))
                        .build();

                client.ingestion().batch(request);
                logger.debug("Generation recorded: {}", data.generationId());
            } catch (Exception e) {
                logger.error("Failed to record generation in Langfuse", e);
            }
        });
    }

    /**
     * Records a span (non-LLM operation) within a trace.
     */
    public void recordSpan(SpanData data) {
        if (!isEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                CreateSpanBody body = CreateSpanBody.builder()
                        .id(data.spanId())
                        .traceId(data.traceId())
                        .name(data.name())
                        .startTime(data.startTime())
                        .endTime(data.endTime())
                        .input(data.input())
                        .output(data.output())
                        .metadata(data.metadata())
                        .build();

                IngestionEvent event = IngestionEvent.spanCreate(
                        CreateSpanEvent.builder()
                                .id(UUID.randomUUID().toString())
                                .timestamp(OffsetDateTime.now().toString())
                                .body(body)
                                .build()
                );

                IngestionRequest request = IngestionRequest.builder()
                        .batch(List.of(event))
                        .build();

                client.ingestion().batch(request);
                logger.debug("Span recorded: {}", data.spanId());
            } catch (Exception e) {
                logger.error("Failed to record span in Langfuse", e);
            }
        });
    }

    private boolean isEnabled() {
        return properties.isEnabled() && properties.isConfigured() && client != null;
    }

    // Data records
    public record GenerationData(
            String generationId,
            String traceId,
            String name,
            String model,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Object input,
            Object output,
            Map<String, Object> metadata,
            UsageData usage,
            String level,
            String statusMessage
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String generationId = UUID.randomUUID().toString();
            private String traceId;
            private String name;
            private String model;
            private OffsetDateTime startTime;
            private OffsetDateTime endTime;
            private Object input;
            private Object output;
            private Map<String, Object> metadata;
            private UsageData usage;
            private String level;
            private String statusMessage;

            public Builder generationId(String id) { this.generationId = id; return this; }
            public Builder traceId(String traceId) { this.traceId = traceId; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public Builder startTime(OffsetDateTime startTime) { this.startTime = startTime; return this; }
            public Builder endTime(OffsetDateTime endTime) { this.endTime = endTime; return this; }
            public Builder input(Object input) { this.input = input; return this; }
            public Builder output(Object output) { this.output = output; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
            public Builder usage(UsageData usage) { this.usage = usage; return this; }
            public Builder level(String level) { this.level = level; return this; }
            public Builder statusMessage(String msg) { this.statusMessage = msg; return this; }

            public GenerationData build() {
                return new GenerationData(generationId, traceId, name, model, startTime, endTime,
                        input, output, metadata, usage, level, statusMessage);
            }
        }
    }

    public record SpanData(
            String spanId,
            String traceId,
            String name,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            Object input,
            Object output,
            Map<String, Object> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String spanId = UUID.randomUUID().toString();
            private String traceId;
            private String name;
            private OffsetDateTime startTime;
            private OffsetDateTime endTime;
            private Object input;
            private Object output;
            private Map<String, Object> metadata;

            public Builder spanId(String id) { this.spanId = id; return this; }
            public Builder traceId(String traceId) { this.traceId = traceId; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder startTime(OffsetDateTime startTime) { this.startTime = startTime; return this; }
            public Builder endTime(OffsetDateTime endTime) { this.endTime = endTime; return this; }
            public Builder input(Object input) { this.input = input; return this; }
            public Builder output(Object output) { this.output = output; return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

            public SpanData build() {
                return new SpanData(spanId, traceId, name, startTime, endTime, input, output, metadata);
            }
        }
    }

    public record UsageData(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        public static UsageData of(int promptTokens, int completionTokens, int totalTokens) {
            return new UsageData(promptTokens, completionTokens, totalTokens);
        }
    }
}
