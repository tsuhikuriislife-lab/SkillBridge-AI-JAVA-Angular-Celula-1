package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.UserEventPublisherPort;
import com.riwi.skillbridge.domain.event.UserAccountEvent;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de salida: publica eventos de cuenta en el exchange topic "skillbridge.events".
 */
@Component
public class RabbitUserEventPublisher implements UserEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitUserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitUserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(UserAccountEvent event) {
        UserEventMessage message = UserEventMessage.from(event);
        String routingKey = event.type().routingKey();

        rabbitTemplate.convertAndSend(RabbitConfiguration.EVENTS_EXCHANGE, routingKey, message, amqpMessage -> {
            amqpMessage.getMessageProperties().setMessageId(message.eventId());
            amqpMessage.getMessageProperties().setHeader("x-event-version", UserAccountEvent.VERSION);
            amqpMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return amqpMessage;
        });

        log.info("Evento publicado: type={} eventId={} subjectId={}",
            routingKey, message.eventId(), message.subject().id());
    }
}
