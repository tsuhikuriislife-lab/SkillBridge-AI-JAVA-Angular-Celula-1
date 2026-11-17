package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.event.UserAccountEvent;

/**
 * Puerto de salida para publicar eventos de cuenta.
 * La capa de aplicación NO sabe que existe RabbitMQ.
 */
public interface UserEventPublisherPort {
    void publish(UserAccountEvent event);
}
