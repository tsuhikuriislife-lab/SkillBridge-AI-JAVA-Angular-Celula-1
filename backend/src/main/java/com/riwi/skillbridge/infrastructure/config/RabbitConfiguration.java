package com.riwi.skillbridge.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class RabbitConfiguration {
    public static final String BOOKING_EXCHANGE = "booking.events";
    public static final String BOOKING_CREATED_QUEUE = "booking.created.queue";
    public static final String BOOKING_CREATED_KEY = "booking.created";
    public static final String DLX = "booking.dlx";
    public static final String DLQ = "booking.created.dlq";
    public static final String DLQ_KEY = "booking.created.failed";
    /** Exchange de eventos de dominio consumidos por notifications-service. Contrato: docs/notifications/CONTRACT.md */
    public static final String EVENTS_EXCHANGE = "skillbridge.events";

    public static final String FEATURE_STORE_EXCHANGE = "feature-store.events";
    public static final String FEATURES_UPDATED_QUEUE = "features.updated.queue";
    public static final String FEATURES_UPDATED_KEY = "features.updated";
    public static final String FEATURE_STORE_DLX = "feature-store.dlx";
    public static final String FEATURES_UPDATED_DLQ = "features.updated.dlq";
    public static final String FEATURES_UPDATED_DLQ_KEY = "features.updated.failed";

    @Bean TopicExchange bookingExchange() { return new TopicExchange(BOOKING_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }
    
    @Bean
    TopicExchange skillbridgeEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }


    @Bean
    Queue bookingCreatedQueue() {
        return QueueBuilder.durable(BOOKING_CREATED_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ_KEY)
                .build();
    }

    @Bean Queue bookingDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean
    Binding bookingCreatedBinding(
            @Qualifier("bookingCreatedQueue") Queue bookingCreatedQueue,
            @Qualifier("bookingExchange") TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingCreatedQueue).to(bookingExchange).with(BOOKING_CREATED_KEY);
    }

    @Bean
    Binding bookingDlqBinding(
            @Qualifier("bookingDeadLetterQueue") Queue bookingDeadLetterQueue,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(bookingDeadLetterQueue).to(deadLetterExchange).with(DLQ_KEY);
    }

    @Bean
    TopicExchange featureStoreExchange() {
        return new TopicExchange(FEATURE_STORE_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange featureStoreDeadLetterExchange() {
        return new DirectExchange(FEATURE_STORE_DLX, true, false);
    }

    @Bean
    Queue featuresUpdatedQueue() {
        return QueueBuilder.durable(FEATURES_UPDATED_QUEUE)
                .deadLetterExchange(FEATURE_STORE_DLX)
                .deadLetterRoutingKey(FEATURES_UPDATED_DLQ_KEY)
                .build();
    }

    @Bean
    Queue featuresUpdatedDeadLetterQueue() {
        return QueueBuilder.durable(FEATURES_UPDATED_DLQ).build();
    }

    @Bean
    Binding featuresUpdatedBinding(
            @Qualifier("featuresUpdatedQueue") Queue featuresUpdatedQueue,
            @Qualifier("featureStoreExchange") TopicExchange featureStoreExchange) {
        return BindingBuilder.bind(featuresUpdatedQueue).to(featureStoreExchange).with(FEATURES_UPDATED_KEY);
    }

    @Bean
    Binding featuresUpdatedDeadLetterBinding(
            @Qualifier("featuresUpdatedDeadLetterQueue") Queue featuresUpdatedDeadLetterQueue,
            @Qualifier("featureStoreDeadLetterExchange") DirectExchange featureStoreDeadLetterExchange) {
        return BindingBuilder.bind(featuresUpdatedDeadLetterQueue)
                .to(featureStoreDeadLetterExchange)
                .with(FEATURES_UPDATED_DLQ_KEY);
    }

    @Bean MessageConverter jacksonMessageConverter() { return new Jackson2JsonMessageConverter(); }
}
