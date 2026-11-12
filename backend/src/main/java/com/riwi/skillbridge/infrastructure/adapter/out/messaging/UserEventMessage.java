package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.domain.event.UserAccountEvent;

import java.util.List;
import java.util.Map;

/**
 * Representación JSON del evento (contrato v1, ver docs/notifications/CONTRACT.md).
 * Todo va como String para no depender de la configuración de fechas/enums de Jackson.
 */
public record UserEventMessage(
    String eventId,
    String eventType,
    int version,
    String occurredAt,
    ActorMessage actor,
    SubjectMessage subject,
    List<AdminMessage> admins,
    Map<String, String> data
) {
    public record ActorMessage(String id, String email, String role) {}
    public record SubjectMessage(String id, String name, String email, String role, String status) {}
    public record AdminMessage(String email, String name) {}

    public static UserEventMessage from(UserAccountEvent event) {
        UserAccountEvent.Actor actor = event.actor();
        UserAccountEvent.Subject subject = event.subject();
        return new UserEventMessage(
            event.eventId().toString(),
            event.type().routingKey(),
            UserAccountEvent.VERSION,
            event.occurredAt().toInstant().toString(),
            actor == null ? null : new ActorMessage(
                actor.id() == null ? null : actor.id().toString(),
                actor.email(),
                actor.role() == null ? null : actor.role().name()),
            new SubjectMessage(
                subject.id().toString(),
                subject.name(),
                subject.email(),
                subject.role().name(),
                subject.status().name()),
            event.admins().stream()
                .map(admin -> new AdminMessage(admin.email(), admin.name()))
                .toList(),
            event.data());
    }
}
