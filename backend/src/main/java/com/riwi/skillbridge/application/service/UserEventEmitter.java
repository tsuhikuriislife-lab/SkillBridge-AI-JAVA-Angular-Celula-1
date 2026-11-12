package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.UserEventPublisherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.event.UserAccountEvent;
import com.riwi.skillbridge.domain.event.UserAccountEvent.Actor;
import com.riwi.skillbridge.domain.event.UserAccountEvent.AdminRecipient;
import com.riwi.skillbridge.domain.event.UserAccountEvent.Subject;
import com.riwi.skillbridge.domain.event.UserEventType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Construye y publica eventos de cuenta. Nunca lanza excepciones hacia el caso de uso:
 * si la publicación falla, se registra un WARN y el flujo de negocio continúa.
 */
@Service
public class UserEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(UserEventEmitter.class);

    private final UserEventPublisherPort publisher;
    private final UserRepositoryPort users;

    public UserEventEmitter(UserEventPublisherPort publisher, UserRepositoryPort users) {
        this.publisher = publisher;
        this.users = users;
    }

    public void registered(UserAccount user) {
        publish(UserEventType.USER_REGISTERED, user, null, Map.of());
    }

    public void createdByAdmin(UserAccount user, String actorEmail) {
        publish(UserEventType.USER_CREATED, user, actorEmail, Map.of());
    }

    public void statusChanged(UserAccount before, UserAccount after, String actorEmail) {
        if (before.status() == after.status()) {
            return; // no hubo cambio real → no se notifica
        }
        publish(UserEventType.USER_STATUS_CHANGED, after, actorEmail, Map.of(
            "previousStatus", before.status().name(),
            "newStatus", after.status().name()));
    }

    public void roleChanged(UserAccount before, UserAccount after, String actorEmail) {
        if (before.role() == after.role()) {
            return;
        }
        publish(UserEventType.USER_ROLE_CHANGED, after, actorEmail, Map.of(
            "previousRole", before.role().name(),
            "newRole", after.role().name()));
    }

    public void deleted(UserAccount deletedUser, String actorEmail) {
        publish(UserEventType.USER_DELETED, deletedUser, actorEmail, Map.of());
    }

    /** Confirmación de reserva: el cliente se inscribió en un servicio. */
    public void enrollmentCreated(UserAccount customer, Offering service, ServiceEnrollment enrollment,
                                  ServiceSchedule schedule) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("serviceId", service.id().toString());
            data.put("serviceName", service.name());
            data.put("serviceCode", service.code() == null ? "" : service.code());
            data.put("startDate", enrollment.startDate() == null ? "" : enrollment.startDate().toString());
            data.put("endDate", enrollment.endDate() == null ? "" : enrollment.endDate().toString());
            data.put("startTime", schedule.startTime() == null ? "" : schedule.startTime().toString());
            data.put("sessions", String.valueOf(enrollment.remainingSessions()));
            if (service.createdBy() != null) {
                users.findById(service.createdBy()).ifPresent(provider -> {
                    data.put("providerName", provider.name());
                    data.put("providerEmail", provider.email());
                });
            }
            publish(UserEventType.ENROLLMENT_CREATED, customer, customer.email(), data);
        } catch (RuntimeException ex) {
            log.warn("No se pudo preparar el evento enrollment.created del usuario {}: {}",
                customer.id(), ex.getMessage());
        }
    }

    private void publish(UserEventType type, UserAccount subject, String actorEmail, Map<String, String> data) {
        try {
            UserAccountEvent event = new UserAccountEvent(
                UUID.randomUUID(),
                type,
                OffsetDateTime.now(ZoneOffset.UTC),
                resolveActor(actorEmail),
                Subject.from(subject),
                resolveAdmins(),
                data);
            publisher.publish(event);
        } catch (RuntimeException ex) {
            log.warn("No se pudo publicar el evento {} del usuario {}: {}",
                type.routingKey(), subject.id(), ex.getMessage());
        }
    }

    private Actor resolveActor(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return null;
        }
        String normalized = actorEmail.trim().toLowerCase();
        return users.findByEmail(normalized)
            .map(actor -> new Actor(actor.id(), actor.email(), actor.role()))
            .orElseGet(() -> new Actor(null, normalized, null));
    }

    private List<AdminRecipient> resolveAdmins() {
        return users.findByRole(Role.ADMIN).stream()
            .filter(admin -> admin.status() == Status.ACTIVE)
            .map(admin -> new AdminRecipient(admin.email(), admin.name()))
            .toList();
    }
}
