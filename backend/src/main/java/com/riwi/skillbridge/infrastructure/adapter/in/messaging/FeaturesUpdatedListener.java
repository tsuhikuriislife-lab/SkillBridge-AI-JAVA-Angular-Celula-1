package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.model.FeaturesUpdatedEvent;
import com.riwi.skillbridge.application.port.in.HandleFeaturesUpdatedUseCase;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class FeaturesUpdatedListener {
    private static final Logger log = LoggerFactory.getLogger(FeaturesUpdatedListener.class);

    private final ObjectMapper objectMapper;
    private final HandleFeaturesUpdatedUseCase useCase;

    public FeaturesUpdatedListener(ObjectMapper objectMapper, HandleFeaturesUpdatedUseCase useCase) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
    }

    @RabbitListener(queues = RabbitConfiguration.FEATURES_UPDATED_QUEUE)
    public void receive(Message message) {
        FeaturesUpdatedEvent event;
        try {
            event = parse(message.getBody());
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Rejecting malformed features.updated message: {}", exception.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid features.updated payload", exception);
        }
        useCase.handle(event);
    }

    private FeaturesUpdatedEvent parse(byte[] body) throws IOException {
        JsonNode json = objectMapper.readTree(body);
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return new FeaturesUpdatedEvent(
                UUID.fromString(requiredText(json, "batch_id")),
                requiredCount(json, "mentores_cargados"),
                requiredCount(json, "embeddings_cargados"),
                requiredCount(json, "embeddings_rechazados"),
                Instant.parse(requiredText(json, "started_at")),
                Instant.parse(requiredText(json, "finished_at")));
    }

    private static String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return value.asText();
    }

    private static int requiredCount(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("Missing or invalid " + field);
        }
        return value.intValue();
    }
}
