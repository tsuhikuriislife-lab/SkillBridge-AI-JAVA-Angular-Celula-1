package com.riwi.skillbridge.domain.event;

/**
 * Tipos de eventos del ciclo de vida de una cuenta de usuario.
 * Cada tipo conoce su routing key (contrato con notifications-service).
 */
public enum UserEventType {
    USER_REGISTERED("user.registered"),
    USER_CREATED("user.created"),
    USER_STATUS_CHANGED("user.status.changed"),
    USER_ROLE_CHANGED("user.role.changed"),
    USER_DELETED("user.deleted"),
    ENROLLMENT_CREATED("enrollment.created");

    private final String routingKey;

    UserEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
