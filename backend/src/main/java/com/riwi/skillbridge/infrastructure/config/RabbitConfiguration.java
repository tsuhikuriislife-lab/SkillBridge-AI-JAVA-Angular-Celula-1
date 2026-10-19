package com.riwi.skillbridge.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue";
    public static final String BOOKING_CREATED_KEY = "booking.created";
    public static final String DLX = "booking.dlx";
    public static final String DLQ = "booking.created.dlq";
    public static final String DLQ_KEY = "booking.created.failed";

    @Bean TopicExchange bookingExchange() { return new TopicExchange(BOOKING_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean
    Queue bookingCreatedQueue() {
        return QueueBuilder.durable(BOOKING_CREATED_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_KEY)
                .build();
    }

    @Bean Queue bookingDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding bookingCreatedBinding(Queue bookingCreatedQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingCreatedQueue).to(bookingExchange).with(BOOKING_CREATED_KEY);
    }

    @Bean
    Binding bookingDlqBinding(Queue bookingDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(bookingDeadLetterQueue).to(deadLetterExchange).with(DLQ_KEY);
    }

    @Bean MessageConverter jacksonMessageConverter() { return new Jackson2JsonMessageConverter(); }
}
