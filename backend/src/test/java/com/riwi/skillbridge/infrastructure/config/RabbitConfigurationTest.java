package com.riwi.skillbridge.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConfigurationTest {
    @Test
    void declaresDurableFeatureStoreExchangeQueueAndDeadLetterRoute() {
        RabbitConfiguration configuration = new RabbitConfiguration();
        TopicExchange exchange = configuration.featureStoreExchange();
        DirectExchange deadLetterExchange = configuration.featureStoreDeadLetterExchange();
        Queue queue = configuration.featuresUpdatedQueue();
        Queue deadLetterQueue = configuration.featuresUpdatedDeadLetterQueue();
        Binding binding = configuration.featuresUpdatedBinding(queue, exchange);
        Binding deadLetterBinding =
                configuration.featuresUpdatedDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertEquals("feature-store.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertEquals("features.updated.queue", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("feature-store.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("features.updated.failed", queue.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("features.updated.dlq", deadLetterQueue.getName());
        assertTrue(deadLetterQueue.isDurable());
        assertEquals("features.updated", binding.getRoutingKey());
        assertEquals("features.updated.failed", deadLetterBinding.getRoutingKey());
    }
}
