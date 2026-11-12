package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.event.UserAccountEvent;
import com.riwi.skillbridge.domain.event.UserEventType;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitUserEventPublisherTest {

    private static final Path CONTRACT_DIR = Path.of("..", "docs", "notifications", "contract");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitUserEventPublisher publisher = new RabbitUserEventPublisher(rabbitTemplate);

    private static UserAccountEvent sampleEvent() {
        return new UserAccountEvent(
            UUID.randomUUID(),
            UserEventType.USER_STATUS_CHANGED,
            OffsetDateTime.now(ZoneOffset.UTC),
            new UserAccountEvent.Actor(UUID.randomUUID(), "ana@test.com", Role.CUSTOMER),
            new UserAccountEvent.Subject(UUID.randomUUID(), "Ana", "ana@test.com", Role.CUSTOMER, Status.INACTIVE),
            List.of(new UserAccountEvent.AdminRecipient("admin@test.com", "Admin")),
            Map.of("previousStatus", "ACTIVE", "newStatus", "INACTIVE"));
    }

    @Test
    void publish_sendsToEventsExchange_withRoutingKeyAndHeaders() throws Exception {
        UserAccountEvent event = sampleEvent();
        ArgumentCaptor<MessagePostProcessor> mpp = ArgumentCaptor.forClass(MessagePostProcessor.class);

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfiguration.EVENTS_EXCHANGE),
            eq("user.status.changed"), any(UserEventMessage.class), mpp.capture());

        Message processed = mpp.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(event.eventId().toString(), processed.getMessageProperties().getMessageId());
        assertEquals(1, (Integer) processed.getMessageProperties().getHeader("x-event-version"));
    }

    @Test
    void message_hasSameJsonShapeAsContractFixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode produced = mapper.valueToTree(UserEventMessage.from(sampleEvent()));
        JsonNode fixture = mapper.readTree(Files.readString(CONTRACT_DIR.resolve("user.status.changed.json")));

        assertEquals(fieldPaths(fixture, ""), fieldPaths(produced, ""),
            "El JSON de Java no coincide con docs/notifications/contract/user.status.changed.json");
    }

    /** Devuelve el conjunto de rutas de campos ("subject.email", "admins[].name", ...). */
    private static Set<String> fieldPaths(JsonNode node, String prefix) {
        Set<String> paths = new HashSet<>();
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                String path = prefix.isEmpty() ? name : prefix + "." + name;
                paths.add(path);
                paths.addAll(fieldPaths(node.get(name), path));
            });
        } else if (node.isArray() && !node.isEmpty()) {
            paths.addAll(fieldPaths(node.get(0), prefix + "[]"));
        }
        return paths;
    }
}
