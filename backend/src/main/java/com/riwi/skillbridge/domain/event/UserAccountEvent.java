package com.riwi.skillbridge.domain.event;

import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho de negocio: "algo cambió en la cuenta de un usuario".
 * Inmutable. No contiene contraseñas ni secretos.
 */
public record UserAccountEvent(
    UUID eventId,
    UserEventType type,
    OffsetDateTime occurredAt,
    Actor actor,
    Subject subject,
    List<AdminRecipient> admins,
    Map<String, String> data
) {
    public static final int VERSION = 1;

    public UserAccountEvent {
        admins = admins == null ? List.of() : List.copyOf(admins);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /** Quién provocó el cambio (null en auto-registro). id/role pueden ser null si no se encontró en BD. */
    public record Actor(UUID id, String email, Role role) {}

    /** Usuario afectado, con su estado YA actualizado. */
    public record Subject(UUID id, String name, String email, Role role, Status status) {
        public static Subject from(UserAccount user) {
            return new Subject(user.id(), user.name(), user.email(), user.role(), user.status());
        }
    }

    /** Administrador que debe recibir copia (Nest decide si la envía según la política). */
    public record AdminRecipient(String email, String name) {}
}
