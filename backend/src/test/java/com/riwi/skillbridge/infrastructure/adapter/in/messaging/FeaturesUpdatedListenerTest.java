package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.application.model.FeaturesUpdatedEvent;
import com.riwi.skillbridge.application.port.in.HandleFeaturesUpdatedUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FeaturesUpdatedListenerTest {
    private final HandleFeaturesUpdatedUseCase useCase = mock(HandleFeaturesUpdatedUseCase.class);
    private final FeaturesUpdatedListener listener = new FeaturesUpdatedListener(new ObjectMapper(), useCase);

    @Test
    void convertsAndPassesValidAndRepeatedMessagesToUseCase() {
        UUID batchId = UUID.randomUUID();
        String payload = """
                {"batch_id":"%s","mentores_cargados":3,"embeddings_cargados":5,
                 "embeddings_rechazados":1,"started_at":"2026-11-11T12:00:00Z",
                 "finished_at":"2026-11-11T12:01:00Z"}
                """.formatted(batchId);
        Message message = message(payload);

        listener.receive(message);
        listener.receive(message);

        verify(useCase, times(2)).handle(new FeaturesUpdatedEvent(
                batchId, 3, 5, 1,
                Instant.parse("2026-11-11T12:00:00Z"),
                Instant.parse("2026-11-11T12:01:00Z")));
    }

    @Test
    void rejectsMalformedPayloadWithoutRequeue() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message("{broken")));
        verify(useCase, org.mockito.Mockito.never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPayloadWithInvalidCountsWithoutRequeue() {
        String payload = """
                {"batch_id":"%s","mentores_cargados":-1,"embeddings_cargados":5,
                 "embeddings_rechazados":0,"started_at":"2026-11-11T12:00:00Z",
                 "finished_at":"2026-11-11T12:01:00Z"}
                """.formatted(UUID.randomUUID());

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message(payload)));
    }

    private static Message message(String payload) {
        return new Message(payload.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
