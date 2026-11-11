# Guía Dylan: HU-NOT00, publicación de eventos desde el backend Java + correcciones de seguridad

> **Jira:** [KAN-59](https://skillbridgec3.atlassian.net/browse/KAN-59) (épica [KAN-49](https://skillbridgec3.atlassian.net/browse/KAN-49))
> **Responsable:** Dylan Suarez (líder de la épica)
> **Rama:** `feature/notificaciones-java` (sale de `feature/notificaciones`)
> **Tiempo estimado:** 4,5 h (ver el cronograma en [PLAN](./PLAN.md); incluye las correcciones de seguridad del Paso 13)
> **Antes de empezar lee:** [CONTRACT.md](./CONTRACT.md). **Orden:** primero sube la base del microservicio ([KAN-56, Bloque 0](./GUIA-DYLAN-HU-NOT01-KAN-56.md)) para desbloquear a Kevin y Leonardo; después haz esta guía.

## Qué vas a construir

Cada vez que cambia el estado de una cuenta, el backend publica un evento en RabbitMQ (`skillbridge.events`). Para hacerlo respetando la arquitectura hexagonal:

```
domain/event/                       ← evento de dominio: Java puro, sin Spring ni RabbitMQ
application/port/out/               ← puerto UserEventPublisherPort (interfaz)
application/service/UserEventEmitter← arma el evento y decide si se publica
infrastructure/adapter/out/messaging← adaptador RabbitMQ: convierte el evento a JSON y lo envía
```

Los servicios existentes (`AuthService`, `AdminUserService`, `UserAccountService` y `ServiceEnrollmentService`) sólo llaman a `UserEventEmitter` después de guardar en BD.

**Eventos que publica el backend (6):** `user.registered`, `user.created`, `user.status.changed`, `user.role.changed`, `user.deleted` y `enrollment.created` (confirmación de reserva).

> **Decisión importante:** si RabbitMQ está caído, **el caso de uso NO falla**. El usuario igual queda registrado; sólo se pierde ese correo y queda un `WARN` en el log. Para garantizar entrega 100 % haría falta el patrón *Transactional Outbox*, que queda como mejora futura (ver [ADR 0005](../adr/0005-notification-microservice-nestjs-resend.md)).

---

## Paso 0: Preparar la rama

```bash
# La rama de integración feature/notificaciones ya la creaste en el Paso 0 de la guía KAN-56.
cd ~/Projects/SkillBridge-AI-JAVA-Angular-Celula-1
git fetch origin
git checkout feature/notificaciones
git pull origin feature/notificaciones

# Rama de trabajo para el backend
git checkout -b feature/notificaciones-java
```

**Por qué una rama de integración:** el CI del repo (`.github/workflows/ci.yml`) hace auto-merge de los PR que apuntan a `feature/*` cuando el build pasa. Cuando `feature/notificaciones` se actualiza, `promote.yml` abre solo el PR hacia `develop`. Así los 3 integran sin romper `develop`.

---

## Paso 1: Tipo de evento (dominio)

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/domain/event/UserEventType.java`

```java
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
```

**Explicación:**
- Es un `enum` de dominio puro: no importa nada de Spring ni de RabbitMQ, como exige `AGENTS.md`.
- El texto `user.status.changed` es la **routing key** y además el `eventType` del JSON. Tenerlo en un solo lugar evita errores de tipeo entre el productor y el consumidor.
- `ENROLLMENT_CREATED` es la confirmación de reserva (KAN-56 pide esa plantilla). Para agregar eventos nuevos más adelante basta con sumar un valor aquí y actualizar el `CONTRACT.md`.

---

## Paso 2: El evento de dominio

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/domain/event/UserAccountEvent.java`

```java
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
```

**Explicación:**
- **`record`:** el proyecto ya modela el dominio con records (`UserAccount`, `Offering`…). Son inmutables y no necesitan getters.
- **Constructor compacto** (`public UserAccountEvent { ... }`): normaliza `null` a colecciones vacías y hace copias inmutables. Así nadie puede modificar la lista después de crear el evento.
- **`Subject.from(user)`:** copia **sólo** los datos que necesita el correo. Fíjate en que **no** copia `password`: el evento nunca debe llevar secretos.
- **`Actor`:** permite que Nest sepa si el cambio lo hizo un admin o el propio usuario, y decida si avisar al admin.
- **`VERSION`:** si algún día cambia el formato de manera incompatible, se sube a 2.

---

## Paso 3: Puerto de salida

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/application/port/out/UserEventPublisherPort.java`

```java
package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.event.UserAccountEvent;

/**
 * Puerto de salida para publicar eventos de cuenta.
 * La capa de aplicación NO sabe que existe RabbitMQ.
 */
public interface UserEventPublisherPort {
    void publish(UserAccountEvent event);
}
```

**Explicación:** es la regla de dependencia de `AGENTS.md` ("toda integración externa pasa por un puerto"). La aplicación depende de esta interfaz. RabbitMQ aparece recién en el Paso 6, en infraestructura. Si mañana cambian a Kafka, sólo se reemplaza el adaptador.

---

## Paso 4: `UserEventEmitter` (aplicación)

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/application/service/UserEventEmitter.java`

```java
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
```

**Explicación por partes:**
1. **¿Por qué una clase aparte?** Sin ella, habría que repetir en 3 servicios la lógica de armar el evento, buscar al actor y buscar a los admins. Además, a los servicios existentes sólo se les agrega **una** dependencia nueva.
2. **Métodos públicos** (`registered`, `statusChanged`…): uno por cada tipo del contrato. `statusChanged` y `roleChanged` reciben el **antes** y el **después**, y si no cambió nada **no publican**. Así se evitan correos del tipo "tu rol cambió de CUSTOMER a CUSTOMER".
3. **`publish(...)` privado:**
   - `UUID.randomUUID()` genera el `eventId` que Nest usa para no enviar duplicados.
   - `OffsetDateTime.now(ZoneOffset.UTC)` fija la fecha en UTC, como pide el contrato.
   - El `try/catch (RuntimeException)` es **intencional**: un fallo de RabbitMQ no debe tumbar el registro ni el cambio de rol.
4. **`resolveActor`:** el controller sólo conoce el email del JWT (`authentication.getName()`). Aquí lo convertimos en un `Actor` con id y rol. Si no existe en BD, se envía igual con `id=null` y `role=null`.
5. **`resolveAdmins`:** usa `UserRepositoryPort.findByRole`, que **ya existe** en el proyecto, y filtra a los admins activos. Nest no tiene acceso a la BD del backend, por eso Java le manda la lista.
6. **`enrollmentCreated`:** arma los datos de la reserva (servicio, fechas, hora, sesiones) y busca al **proveedor** (`Offering.createdBy`) para que Nest también le avise. Todos los valores van como `String` (contrato), con `""` cuando faltan, porque `Map.copyOf` no acepta `null`. Tiene su propio `try/catch`: buscar al proveedor también podría fallar y no debe romper la inscripción.

---

## Paso 5: Declarar el exchange en `RabbitConfiguration`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java`

Agrega **la constante** debajo de las constantes existentes:

```java
    /** Exchange de eventos de dominio consumidos por notifications-service. Contrato: docs/notifications/CONTRACT.md */
    public static final String EVENTS_EXCHANGE = "skillbridge.events";
```

Agrega **el bean** debajo de `deadLetterExchange()`:

```java
    @Bean
    TopicExchange skillbridgeEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }
```

**Explicación:**
- `TopicExchange(nombre, durable=true, autoDelete=false)`: el exchange sobrevive a reinicios de RabbitMQ. Es de tipo *topic* para que Nest pueda suscribirse con comodines (`user.#`).
- Spring AMQP (`RabbitAdmin`, que se configura solo) declara el exchange al abrir la primera conexión.
- **No borres** los beans `booking.*`: son legacy pero otro equipo podría usarlos. Ya hay **dos** beans `TopicExchange`. Spring resuelve el parámetro `TopicExchange bookingExchange` de `bookingCreatedBinding` **por nombre de parámetro**, y funciona porque `spring-boot-starter-parent` compila con `-parameters`. Si alguna vez falla con `NoUniqueBeanDefinitionException`, agrega `@Qualifier("bookingExchange")` a ese parámetro.
- No hace falta tocar el `MessageConverter`: el bean `Jackson2JsonMessageConverter` que ya existe hace que `RabbitTemplate` envíe JSON.

---

## Paso 6: Adaptador RabbitMQ (infraestructura)

### 6.1 El mensaje JSON (forma exacta del contrato)

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/UserEventMessage.java`

```java
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
```

**Explicación:**
- **¿Por qué otro record y no serializar `UserAccountEvent` directo?** Porque el JSON es un **contrato público**. Si alguien renombra un campo del dominio, el contrato no debe romperse en silencio. Este DTO es la "traducción" oficial.
- **Todo como `String`:** el `Jackson2JsonMessageConverter` del proyecto usa un `ObjectMapper` básico **sin** el módulo de fechas de Java 8. Si mandáramos `OffsetDateTime`, fallaría o saldría como número. `toInstant().toString()` produce `2026-11-11T15:10:00.123Z` (ISO-8601 en UTC), que es lo que valida Nest.
- `eventType` usa `routingKey()`, así que es idéntico a la routing key.

### 6.2 El publicador

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitUserEventPublisher.java`

```java
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
```

**Explicación:**
- `@Component` en infraestructura implementa el puerto, y Spring lo inyecta donde se pida `UserEventPublisherPort`.
- **`RabbitTemplate`:** Spring Boot lo crea solo a partir de `spring.rabbitmq.addresses` (variable `RABBITMQ_URL`, que ya está en `application.yml` y en compose) y le aplica el `MessageConverter` JSON existente.
- `convertAndSend(exchange, routingKey, objeto, postProcessor)` convierte el record a JSON. El *post processor* (la lambda) agrega metadatos AMQP:
  - `messageId = eventId`: sirve para rastrear el mensaje en la UI de RabbitMQ.
  - `x-event-version`: versión del contrato.
  - `PERSISTENT`: el mensaje se guarda en disco y sobrevive a reinicios del broker.
- **No se capturan excepciones aquí.** Suben a `UserEventEmitter`, que las registra como `WARN`. Cada capa tiene una sola responsabilidad.
- El log **no** imprime el email (dato personal), sólo IDs.

---

## Paso 7: Agregar el actor a los casos de uso de usuario

Hoy `changeStatus` y `deleteUserAccount` no saben **quién** hizo el cambio. Sin ese dato, Nest no puede decidir si avisar al admin.

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/port/in/useraccount/ChangeStatusUserAccountUseCase.java`

```java
package com.riwi.skillbridge.application.port.in.useraccount;

import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface ChangeStatusUserAccountUseCase {
    Optional<UserAccount> changeStatus(UUID id, boolean active, String actorEmail);
}
```

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/port/in/useraccount/DeleteUserAccountUseCase.java`

```java
package com.riwi.skillbridge.application.port.in.useraccount;

import java.util.UUID;

public interface DeleteUserAccountUseCase {
    boolean deleteUserAccount(UUID id, String actorEmail);
}
```

**Explicación:** es el mismo patrón que ya usa `AdminManageUsersUseCase` (`createUser(..., actorEmail)`). El compilador te va a marcar todos los lugares que debes actualizar en los pasos 8 y 11.

---

## Paso 8: Publicar desde `UserAccountService`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/UserAccountService.java`

**8.1 Constructor:** reemplaza el campo y el constructor actuales por:

```java
    private final UserAccountPort userAccountPort;
    private final UserEventEmitter events;

    public UserAccountService(UserAccountPort userAccountPort, UserEventEmitter events) {
        this.userAccountPort = userAccountPort;
        this.events = events;
    }
```

**8.2 `changeStatus`:** reemplaza el método completo por:

```java
    @Override
    public Optional<UserAccount> changeStatus(UUID id, boolean active, String actorEmail) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        UserAccount saved = userAccountPort.save(new UserAccount(
            existing.id(),
            existing.name(),
            existing.email(),
            existing.role(),
            existing.image(),
            existing.password(),
            existing.gender(),
            existing.birthDate(),
            active ? Status.ACTIVE : Status.INACTIVE,
            existing.createdAt(),
            OffsetDateTime.now()
        ));

        events.statusChanged(existing, saved, actorEmail);
        return Optional.of(saved);
    }
```

**8.3 `deleteUserAccount`:** reemplaza el método completo por:

```java
    @Override
    public boolean deleteUserAccount(UUID id, String actorEmail) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 🛡️ REGLA 4 (bonus): no eliminar administradores
        if (existing.role() == Role.ADMIN) {
            throw new BusinessRuleException("No se puede eliminar un usuario administrador");
        }

        userAccountPort.deleteById(id);
        events.deleted(existing, actorEmail);
        return true;
    }
```

**Explicación:**
- El evento se emite **después** de `save` / `deleteById`. Si la BD falla, no se manda un correo de algo que no pasó. El proyecto no usa `@Transactional`: cada `save` de Spring Data confirma su propia transacción, así que al llegar a `events...` el cambio ya está en BD.
- En `deleteUserAccount` se usa `existing`, el usuario **leído antes de borrar**. Después del `delete` ya no se podría leer su email.

---

## Paso 9: Publicar desde `AdminUserService`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/AdminUserService.java`

**9.1 Campos y constructor:**

```java
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final AdminActivityUseCase adminActivity;
    private final UserEventEmitter events;

    public AdminUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
                            AdminActivityUseCase adminActivity, UserEventEmitter events) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.adminActivity = adminActivity;
        this.events = events;
    }
```

**9.2 En `createUser`:** justo **después** de `adminActivity.record(actorEmail, "USUARIO CREADO", ...)` y **antes** de `return saved;`:

```java
        events.createdByAdmin(saved, actorEmail);
```

**9.3 En `updateUser`:** justo **después** de `adminActivity.record(actorEmail, action, "USER", saved.id(), message);` y antes de `return saved;`:

```java
        events.roleChanged(current, saved, actorEmail);
```

**9.4 En `deleteUser`:** al final del método, después de `adminActivity.record(...)`:

```java
        events.deleted(user, actorEmail);
```

**Explicación:**
- `createUser` crea la cuenta con una contraseña temporal (`TemporaryPass123*` si no se envía una). **No** la mandamos por correo: el evento no lleva contraseñas y la plantilla de Nest le pide al usuario contactar al admin.
- `updateUser` también cambia nombre y email. `roleChanged` sólo publica si **cambió el rol**: los otros cambios no son "cambios de estado".
- Se emite después de `adminActivity.record(...)`, así el registro de auditoría interno (que ya existe) siempre queda primero.

---

## Paso 10: Publicar desde `AuthService` (registro)

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java`

**10.1 Campos y constructor:**

```java
    private final UserAccountPort users;
    private final PasswordHasherPort passwords;
    private final TokenPort tokens;
    private final UserEventEmitter events;

    public AuthService(UserAccountPort users, PasswordHasherPort passwords, TokenPort tokens,
                       UserEventEmitter events) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.events = events;
    }
```

**10.2 En `register`:** reemplaza la última línea `return tokens.generate(saved.email(), saved.role().name());` por:

```java
        events.registered(saved);
        return tokens.generate(saved.email(), saved.role().name());
```

**Explicación:** en el auto-registro no hay actor (el usuario aún no tenía sesión), por eso `registered(saved)` no recibe `actorEmail` y el evento sale con `actor: null`.

---

## Paso 10B: Publicar la confirmación de reserva (`ServiceEnrollmentService`)

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/ServiceEnrollmentService.java`

**10B.1 Import:**

```java
import com.riwi.skillbridge.domain.model.UserAccount;
```

**10B.2 Campos y constructor:**

```java
    private final ServiceEnrollmentPort enrollmentPort;
    private final OfferingPort offeringPort;
    private final ServiceSchedulePort schedulePort;
    private final UserAccountPort userAccountPort;
    private final BookingHistoryPort historyPort;
    private final UserEventEmitter events;

    public ServiceEnrollmentService(ServiceEnrollmentPort enrollmentPort, OfferingPort offeringPort,
                                    ServiceSchedulePort schedulePort, UserAccountPort userAccountPort,
                                    BookingHistoryPort historyPort, UserEventEmitter events) {
        this.enrollmentPort = enrollmentPort;
        this.offeringPort = offeringPort;
        this.schedulePort = schedulePort;
        this.userAccountPort = userAccountPort;
        this.historyPort = historyPort;
        this.events = events;
    }
```

**10B.3 En `enroll`:** cambia la primera instrucción para **guardar** al usuario en una variable:

```java
        UserAccount customer = userAccountPort.findById(request.userId())
                .orElseThrow(() -> new BusinessRuleException("El usuario no existe"));
```

y al final del método reemplaza:

```java
        recordHistory(saved, EnrollmentStatus.ACTIVE);
        return saved;
```

por:

```java
        recordHistory(saved, EnrollmentStatus.ACTIVE);
        events.enrollmentCreated(customer, service, saved, schedule);
        return saved;
```

**Explicación:**
- Antes, `enroll` validaba que el usuario existiera y **descartaba** el resultado. Ahora lo guardamos en `customer` porque el correo necesita su nombre y su email.
- El evento se emite **después** de `save` y `recordHistory`: sólo se confirma una reserva que realmente quedó guardada.
- `schedule` ya estaba cargado en el método (fecha de inicio, hora y sesiones), así que no se hace ninguna consulta extra.

## Paso 11: Controller `UserAccountController`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/UserAccountController.java`

Reemplaza los métodos `changeStatus` y `delete`:

```java
    // PATCH /users/{id}/status — activar/desactivar
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserAccountOut> changeStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody ChangeStatusRequest request,
                                                       Authentication authentication) {
        return changeStatusUseCase.changeStatus(id, request.active(), actorEmail(authentication))
            .map(u -> ResponseEntity.ok(toOut(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /users/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        return deleteUseCase.deleteUserAccount(id, actorEmail(authentication))
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    private static String actorEmail(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
```

Verifica que el import exista (el método `getCurrentUser` ya usa `Authentication`, así que probablemente ya está):

```java
import org.springframework.security.core.Authentication;
```

**Explicación:** Spring inyecta `Authentication` en cualquier método de un controller. El JWT del proyecto usa el **email** como *subject*, así que `getName()` devuelve el email. El helper `actorEmail` evita un `NullPointerException` en pruebas sin seguridad.

> `AdminUserController` **no se toca**: ya le pasa `authentication.getName()` a `createUser/updateUser/deleteUser`.

---

## Paso 12: Pruebas

### 12.1 Ajustar las pruebas existentes (si no, no compilan)

📝 `backend/src/test/java/com/riwi/skillbridge/UserAccountServiceTest.java`
- Debajo de `@Mock UserAccountPort userAccountPort;` agrega:
  ```java
  @Mock com.riwi.skillbridge.application.service.UserEventEmitter events;
  ```
- Cambia las llamadas:
  - `service.changeStatus(id, false)` → `service.changeStatus(id, false, "ana@x.com")`
  - `service.changeStatus(id, true)` → `service.changeStatus(id, true, "ana@x.com")`
  - `service.deleteUserAccount(id)` → `service.deleteUserAccount(id, "ana@x.com")` (2 lugares)

📝 `backend/src/test/java/com/riwi/skillbridge/ServiceEnrollmentServiceTest.java`
- Debajo de los demás `@Mock` agrega (sin él, `@InjectMocks` pasa `null` y `enroll` lanza `NullPointerException`):
  ```java
  @Mock com.riwi.skillbridge.application.service.UserEventEmitter events;
  ```

📝 `backend/src/test/java/com/riwi/skillbridge/application/service/AdminUserServiceTest.java`
- Agrega el mock:
  ```java
  @Mock
  private UserEventEmitter events;
  ```
- En `setUp()`:
  ```java
  adminUserService = new AdminUserService(userRepository, passwordHasher, adminActivity, events);
  ```

**Explicación:** `@InjectMocks` (en `UserAccountServiceTest`) usa el constructor más grande y le pasa los `@Mock` que coincidan por tipo. Por eso basta con declarar el mock. Mockito 5 (incluido en Spring Boot 3.5) puede mockear clases concretas como `UserEventEmitter`.

### 12.2 Prueba nueva: `UserEventEmitterTest`

📄 **Crear** `backend/src/test/java/com/riwi/skillbridge/application/service/UserEventEmitterTest.java`

```java
package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.UserEventPublisherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.event.UserAccountEvent;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.event.UserEventType;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventEmitterTest {

    @Mock UserEventPublisherPort publisher;
    @Mock UserRepositoryPort users;
    @InjectMocks UserEventEmitter emitter;
    @Captor ArgumentCaptor<UserAccountEvent> captor;

    private static UserAccount user(String email, Role role, Status status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UserAccount(UUID.randomUUID(), "Nombre " + role, email, role, null, "hash",
            null, null, status, now, now);
    }

    private static UserAccount withStatus(UserAccount u, Status status) {
        return new UserAccount(u.id(), u.name(), u.email(), u.role(), u.image(), u.password(),
            u.gender(), u.birthDate(), status, u.createdAt(), u.updatedAt());
    }

    @Test
    void statusChanged_publishesPreviousAndNewStatus_withActorAndAdmins() {
        UserAccount admin = user("admin@test.com", Role.ADMIN, Status.ACTIVE);
        UserAccount before = user("ana@test.com", Role.CUSTOMER, Status.ACTIVE);
        UserAccount after = withStatus(before, Status.INACTIVE);
        when(users.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        emitter.statusChanged(before, after, "Admin@Test.com ");

        verify(publisher).publish(captor.capture());
        UserAccountEvent event = captor.getValue();
        assertEquals(UserEventType.USER_STATUS_CHANGED, event.type());
        assertEquals(Map.of("previousStatus", "ACTIVE", "newStatus", "INACTIVE"), event.data());
        assertEquals(Status.INACTIVE, event.subject().status());
        assertEquals(Role.ADMIN, event.actor().role());
        assertEquals(1, event.admins().size());
        assertNotNull(event.eventId());
    }

    @Test
    void statusChanged_whenStatusDidNotChange_doesNotPublish() {
        UserAccount same = user("ana@test.com", Role.CUSTOMER, Status.ACTIVE);
        emitter.statusChanged(same, same, "ana@test.com");
        verifyNoInteractions(publisher);
    }

    @Test
    void roleChanged_whenRoleDidNotChange_doesNotPublish() {
        UserAccount same = user("ana@test.com", Role.CUSTOMER, Status.ACTIVE);
        emitter.roleChanged(same, same, "admin@test.com");
        verifyNoInteractions(publisher);
    }

    @Test
    void registered_hasNullActor_andIgnoresInactiveAdmins() {
        UserAccount inactiveAdmin = user("old-admin@test.com", Role.ADMIN, Status.INACTIVE);
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(inactiveAdmin));

        emitter.registered(user("ana@test.com", Role.CUSTOMER, Status.ACTIVE));

        verify(publisher).publish(captor.capture());
        assertNull(captor.getValue().actor());
        assertTrue(captor.getValue().admins().isEmpty());
    }

    @Test
    void publisherFailure_isSwallowed_soBusinessFlowContinues() {
        doThrow(new RuntimeException("RabbitMQ caído")).when(publisher).publish(any());
        assertDoesNotThrow(() -> emitter.registered(user("ana@test.com", Role.CUSTOMER, Status.ACTIVE)));
    }

    @Test
    void enrollmentCreated_includesServiceScheduleAndProvider() {
        UserAccount customer = user("ana@test.com", Role.CUSTOMER, Status.ACTIVE);
        UserAccount provider = user("prov@test.com", Role.PROVIDER, Status.ACTIVE);
        Offering service = new Offering(UUID.randomUUID(), "Mentoría Spring", null, BigDecimal.TEN, null,
                null, null, null, 10, "SRV-1", ServiceStatus.ACTIVE, provider.id(),
                OffsetDateTime.now(), OffsetDateTime.now());
        ServiceSchedule schedule = new ServiceSchedule(UUID.randomUUID(), service.id(), DayOfWeek.MONDAY, 60,
                null, 5, LocalDate.of(2026, 11, 20), LocalTime.of(18, 0), LocalTime.of(19, 0));
        ServiceEnrollment enrollment = new ServiceEnrollment(customer.id(), service.id(),
                EnrollmentStatus.ACTIVE, 5, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 18));
        when(users.findById(provider.id())).thenReturn(Optional.of(provider));
        when(users.findByEmail("ana@test.com")).thenReturn(Optional.of(customer));

        emitter.enrollmentCreated(customer, service, enrollment, schedule);

        verify(publisher).publish(captor.capture());
        UserAccountEvent event = captor.getValue();
        assertEquals(UserEventType.ENROLLMENT_CREATED, event.type());
        assertEquals("Mentoría Spring", event.data().get("serviceName"));
        assertEquals("2026-11-20", event.data().get("startDate"));
        assertEquals("18:00", event.data().get("startTime"));
        assertEquals("5", event.data().get("sessions"));
        assertEquals("prov@test.com", event.data().get("providerEmail"));
    }
}
```

**Explicación de cada prueba:**
1. **Camino feliz:** verifica `data`, el `subject` con el estado nuevo, el actor resuelto (el email se normaliza: `"Admin@Test.com "` pasa a `admin@test.com`) y la lista de admins.
2. y 3. **Sin cambio real, no hay evento.** Esto evita correos basura.
4. **Auto-registro:** `actor` es `null` y no se incluyen admins inactivos.
5. **Resiliencia:** si RabbitMQ falla, el caso de uso sigue funcionando.
6. **Reserva:** el evento lleva el servicio, la fecha (`2026-11-20`), la hora (`18:00`), las sesiones y el correo del proveedor, todo como texto.

### 12.3 Prueba nueva: el adaptador respeta el contrato

📄 **Crear** `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitUserEventPublisherTest.java`

```java
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
```

**Explicación:**
- **Primera prueba:** se captura el `MessagePostProcessor` y se ejecuta sobre un mensaje vacío para comprobar que pone `messageId` y `x-event-version`. No hace falta un RabbitMQ real.
- **Segunda prueba (prueba de contrato):** convierte el mensaje a JSON y compara **la estructura** (los nombres de todos los campos, incluso los anidados) con el fixture que también usa Nest. Si alguien renombra `subject.email` en Java, esta prueba falla antes de llegar a producción.
- `Path.of("..", "docs", ...)` funciona porque Maven ejecuta las pruebas desde `backend/`. El `Dockerfile` usa `-DskipTests`, así que no se rompe el build de la imagen.

### 12.4 Ejecutar

```bash
# Desde la raíz del repo: compila y corre TODAS las pruebas del backend (igual que CI)
mvn -f backend/pom.xml -B clean verify

# Sólo las pruebas nuevas y las modificadas (más rápido mientras desarrollas)
mvn -f backend/pom.xml test -Dtest='UserEventEmitterTest,RabbitUserEventPublisherTest,UserAccountServiceTest,AdminUserServiceTest,AuthServiceTest,ServiceEnrollmentServiceTest,AuthSessionSecurityTest'
```

---

## Paso 13 (OBLIGATORIO, aprobado por el equipo): corregir las fallas de seguridad de los estados de cuenta

Durante el análisis aparecieron tres problemas en los mismos casos de uso que estamos tocando:

| # | Problema | Riesgo | Arreglo |
|---|---|---|---|
| 1 | `PATCH /api/users/{id}/status` y `DELETE /api/users/{id}` no tienen autorización: **cualquier** usuario con sesión puede desactivar o borrar a otro | Crítico | Sólo un ADMIN o el propio usuario (13.1 – 13.3) |
| 2 | Se puede **desactivar al único ADMIN**: la plataforma queda sin administrador | Alto | Prohibirlo, igual que ya se prohíbe borrarlo (13.3) |
| 3 | `login` entrega un JWT a un usuario INACTIVE | Medio | Rechazar el login (13.4) |

> **Cómo funciona hoy la seguridad:** `DatabaseUserDetailsService` (usado por `JwtAuthenticationFilter` en **cada** petición) ya rechaza a los usuarios INACTIVE. Por eso, cuando alguien es desactivado, su sesión deja de funcionar al instante. Lo que falla es el **login**: responde "OK" con un token que no sirve para nada, y el usuario no entiende qué pasa. Con el arreglo 3 recibe un mensaje claro. **Consecuencia:** un usuario inactivo no puede reactivarse solo; **sólo un ADMIN** puede reactivarlo.

### 13.1 Nueva excepción de dominio (403)

📄 **Crear** `backend/src/main/java/com/riwi/skillbridge/domain/exception/AccountAccessDeniedException.java`

```java
package com.riwi.skillbridge.domain.exception;

/** El actor no tiene permiso para modificar la cuenta indicada (se traduce a HTTP 403). */
public class AccountAccessDeniedException extends BusinessRuleException {
    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
```

**Explicación:** se sigue el patrón que ya usa el proyecto con `UnauthorizedMeetingAccessException`. Al extender `BusinessRuleException`, cualquier código que ya capture esa excepción sigue funcionando. El dominio no sabe nada de HTTP: el código 403 se asigna en infraestructura (13.2).

### 13.2 Traducirla a 403 en el `GlobalExceptionHandler`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java`. Agrega este método antes de la última `}` de la clase:

```java
    @ExceptionHandler(com.riwi.skillbridge.domain.exception.AccountAccessDeniedException.class)
    ProblemDetail accountAccessForbidden(com.riwi.skillbridge.domain.exception.AccountAccessDeniedException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        p.setTitle("Forbidden");
        return p;
    }
```

**Explicación:** Spring elige el `@ExceptionHandler` **más específico**. Aunque existe uno para `BusinessRuleException` (422), para esta subclase gana el nuevo (403 Forbidden). 403 es el código correcto: el usuario está autenticado pero no tiene permiso.

### 13.3 Autorización y protección del ADMIN en `UserAccountService`

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/UserAccountService.java`

Agrega el import:

```java
import com.riwi.skillbridge.domain.exception.AccountAccessDeniedException;
```

Reemplaza **otra vez** `changeStatus` y `deleteUserAccount` (esta es la versión final, que incluye lo del Paso 8) y agrega el método privado `ensureCanManage`:

```java
    @Override
    public Optional<UserAccount> changeStatus(UUID id, boolean active, String actorEmail) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 🛡️ REGLA 5: sólo un ADMIN o el propio usuario
        ensureCanManage(existing, actorEmail);

        // 🛡️ REGLA 4: la plataforma nunca se queda sin administrador activo
        if (existing.role() == Role.ADMIN && !active) {
            throw new BusinessRuleException("No se puede desactivar la cuenta del administrador");
        }

        UserAccount saved = userAccountPort.save(new UserAccount(
            existing.id(),
            existing.name(),
            existing.email(),
            existing.role(),
            existing.image(),
            existing.password(),
            existing.gender(),
            existing.birthDate(),
            active ? Status.ACTIVE : Status.INACTIVE,
            existing.createdAt(),
            OffsetDateTime.now()
        ));

        events.statusChanged(existing, saved, actorEmail);
        return Optional.of(saved);
    }

    @Override
    public boolean deleteUserAccount(UUID id, String actorEmail) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 🛡️ REGLA 4 (bonus): no eliminar administradores
        if (existing.role() == Role.ADMIN) {
            throw new BusinessRuleException("No se puede eliminar un usuario administrador");
        }

        // 🛡️ REGLA 5: sólo un ADMIN o el propio usuario
        ensureCanManage(existing, actorEmail);

        userAccountPort.deleteById(id);
        events.deleted(existing, actorEmail);
        return true;
    }

    /** Lanza 403 si el actor no es ADMIN ni el dueño de la cuenta. */
    private void ensureCanManage(UserAccount target, String actorEmail) {
        UserAccount actor = (actorEmail == null || actorEmail.isBlank())
            ? null
            : userAccountPort.findByEmail(actorEmail.trim().toLowerCase()).orElse(null);

        boolean isAdmin = actor != null && actor.role() == Role.ADMIN;
        boolean isSelf = actor != null && actor.id().equals(target.id());

        if (!isAdmin && !isSelf) {
            throw new AccountAccessDeniedException(
                "Sólo un administrador o el propio usuario puede modificar esta cuenta");
        }
    }
```

**Explicación:**
- **¿Por qué en el servicio y no con `@PreAuthorize` en el controller?** La regla "ADMIN **o** dueño de la cuenta" depende de **datos** (comparar el id del actor con el de la cuenta). En el servicio es Java puro y se prueba con Mockito, sin levantar Spring Security. Además, protege el caso de uso aunque mañana se llame desde otro controller.
- **`ensureCanManage`** busca al actor por el email del JWT. Si no viene email, o no existe, se trata como "sin permisos" (falla cerrado: ante la duda, se niega).
- **Orden de las reglas:**
  - En `changeStatus`: primero la **autorización** y después "no desactivar al ADMIN". Así un usuario cualquiera recibe 403 y no se entera de que la cuenta es de un admin.
  - En `deleteUserAccount` se deja la regla de ADMIN primero, como ya estaba, para no cambiar el comportamiento existente.
- **Reactivar al ADMIN (`active=true`) sí se permite.** Sólo se bloquea desactivarlo.
- El evento se publica **sólo** si pasaron todas las validaciones. Si alguien recibe 403, no sale ningún correo.

### 13.4 Rechazar el login de cuentas INACTIVE (`AuthService`)

📝 **Modificar** `backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java`. Reemplaza el método `login` completo:

```java
    @Override
    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
        if (!passwords.matches(rawPassword, user.password())) {   // ✅ accessor del record
            throw new InvalidCredentialsException("Credenciales inválidas");
        }
        if (user.status() == Status.INACTIVE) {
            throw new InvalidCredentialsException(
                "Tu cuenta está desactivada. Contacta al administrador para reactivarla.");
        }
        return tokens.generate(user.email(), user.role().name());
    }
```

**Explicación:**
- El chequeo va **después** de validar la contraseña. Si fuera antes, cualquiera podría averiguar qué correos existen y están desactivados sin conocer la clave (*enumeración de usuarios*). Así, sólo quien tiene la contraseña correcta ve el motivo real.
- `InvalidCredentialsException` ya se traduce a **401** en el `GlobalExceptionHandler`, así que el frontend lo trata como un login fallido y muestra el mensaje.
- `Status` ya está importado en `AuthService` (lo usa `register`).

### 13.5 Pruebas

📝 **Modificar** `backend/src/test/java/com/riwi/skillbridge/UserAccountServiceTest.java`

**a)** Imports nuevos (junto a los existentes):

```java
import com.riwi.skillbridge.domain.exception.AccountAccessDeniedException;
```

**b)** Reemplaza el test `changeStatus_mapsBooleanToEnum` por esta versión (el actor ahora debe resolverse) y agrega los tests nuevos debajo:

```java
    @Test
    void changeStatus_mapsBooleanToEnum() {
        UserAccount self = existing(Role.CUSTOMER);
        when(userAccountPort.findById(id)).thenReturn(Optional.of(self));
        when(userAccountPort.findByEmail("ana@x.com")).thenReturn(Optional.of(self));
        when(userAccountPort.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(Status.INACTIVE, service.changeStatus(id, false, "ana@x.com").get().status());
        assertEquals(Status.ACTIVE, service.changeStatus(id, true, "ana@x.com").get().status());
    }

    @Test
    void changeStatus_byAnotherNonAdminUser_isForbidden() {
        UserAccount intruder = new UserAccount(UUID.randomUUID(), "Eve", "eve@x.com", Role.CUSTOMER,
                null, "hash", null, null, Status.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        when(userAccountPort.findByEmail("eve@x.com")).thenReturn(Optional.of(intruder));

        assertThrows(AccountAccessDeniedException.class, () -> service.changeStatus(id, false, "eve@x.com"));
        verify(userAccountPort, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void changeStatus_byAdmin_isAllowed() {
        UserAccount admin = new UserAccount(UUID.randomUUID(), "Admin", "admin@x.com", Role.ADMIN,
                null, "hash", null, null, Status.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        when(userAccountPort.findByEmail("admin@x.com")).thenReturn(Optional.of(admin));
        when(userAccountPort.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(Status.INACTIVE, service.changeStatus(id, false, "admin@x.com").get().status());
    }

    @Test
    void changeStatus_cannotDeactivateAdmin() {
        UserAccount admin = existing(Role.ADMIN);
        when(userAccountPort.findById(id)).thenReturn(Optional.of(admin));
        when(userAccountPort.findByEmail("ana@x.com")).thenReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(id, false, "ana@x.com"));
        verify(userAccountPort, never()).save(any());
    }

    @Test
    void changeStatus_withoutActor_isForbidden() {
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        assertThrows(AccountAccessDeniedException.class, () -> service.changeStatus(id, false, null));
    }

    @Test
    void delete_byAnotherNonAdminUser_isForbidden() {
        UserAccount intruder = new UserAccount(UUID.randomUUID(), "Eve", "eve@x.com", Role.CUSTOMER,
                null, "hash", null, null, Status.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        when(userAccountPort.findByEmail("eve@x.com")).thenReturn(Optional.of(intruder));

        assertThrows(AccountAccessDeniedException.class, () -> service.deleteUserAccount(id, "eve@x.com"));
        verify(userAccountPort, never()).deleteById(any());
    }
```

> Los tests `delete_rejectsAdmin` y `delete_throwsWhenNotFound` **no** cambian, salvo el tercer argumento que ya agregaste en el Paso 12.1. Siguen pasando porque fallan antes de llegar a la autorización.
> El campo del mock debe llamarse `events` (`@Mock ... UserEventEmitter events;`, Paso 12.1) para que `verifyNoInteractions(events)` compile.

📄 **Crear** `backend/src/test/java/com/riwi/skillbridge/application/service/AuthServiceTest.java`

```java
package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserAccountPort users;
    @Mock PasswordHasherPort passwords;
    @Mock TokenPort tokens;
    @Mock UserEventEmitter events;
    @InjectMocks AuthService authService;

    private static UserAccount user(Status status) {
        return new UserAccount(UUID.randomUUID(), "Ana", "ana@x.com", Role.CUSTOMER, null, "hash",
                null, null, status, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void login_activeUser_returnsToken() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.ACTIVE)));
        when(passwords.matches("Clave123*", "hash")).thenReturn(true);
        when(tokens.generate("ana@x.com", "CUSTOMER")).thenReturn("jwt");

        assertEquals("jwt", authService.login(" Ana@X.com ", "Clave123*"));
    }

    @Test
    void login_inactiveUser_isRejected_andNoTokenIsIssued() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.INACTIVE)));
        when(passwords.matches("Clave123*", "hash")).thenReturn(true);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("ana@x.com", "Clave123*"));
        assertTrue(ex.getMessage().contains("desactivada"));
        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void login_inactiveUser_withWrongPassword_doesNotRevealStatus() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.INACTIVE)));
        when(passwords.matches("mala", "hash")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("ana@x.com", "mala"));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void register_publishesRegisteredEvent() {
        when(users.existsByEmail("nuevo@x.com")).thenReturn(false);
        when(passwords.encode("Clave123*")).thenReturn("hash");
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokens.generate("nuevo@x.com", "CUSTOMER")).thenReturn("jwt");

        authService.register("Nuevo", "nuevo@x.com", "Clave123*");

        verify(events).registered(any(UserAccount.class));
    }
}
```

**Explicación de las pruebas nuevas:**
- **Autorización:**
  - Un intruso recibe 403, **no** se guarda nada y **no** se publica ningún evento (no sale correo).
  - El ADMIN sí puede.
  - Sin actor, 403 (falla cerrado).
- **Protección del ADMIN:** no se puede desactivar.
- **Login:**
  - Una cuenta inactiva no recibe token.
  - Con contraseña incorrecta, el mensaje **no** revela que la cuenta está desactivada.
- **Registro:** de paso, se prueba que publica `user.registered` (Paso 10).

### 13.6 Verificación manual

```bash
# Login como usuario A (CUSTOMER); guarda su cookie de sesión
curl -s -c /tmp/sb-a -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"<correo-A>","password":"<clave-A>"}'

# Intentar desactivar al usuario B con la sesión de A → debe responder 403
curl -s -o /dev/null -w "%{http_code}\n" -b /tmp/sb-a -X PATCH \
  http://localhost:8081/api/users/<id-B>/status \
  -H 'Content-Type: application/json' -d '{"active": false}'

# A se desactiva a sí mismo → 200; después, su login responde 401 con "Tu cuenta está desactivada..."
```

> Si el backend exige el header CSRF en peticiones con cookie, la forma más simple de probar es desde Swagger (`http://localhost:8081/swagger-ui.html`) o desde el frontend.

> **Pendiente fuera de esta épica (anotar en Jira):** el panel de administración de Angular **no** tiene botón para activar o desactivar usuarios (sólo cambia roles). Hoy el admin sólo puede hacerlo por API o Swagger.

---

## Paso 14: Verificación manual (con todo levantado)

```bash
# 1. Levantar todo (backend + rabbit + notifications de las otras personas)
make up

# 2. Ver los logs del backend filtrando eventos publicados
docker compose logs -f backend | grep -i "Evento publicado"
```

En otra terminal:

```bash
# 3. Registrar un usuario: debe aparecer "Evento publicado: type=user.registered"
curl -s -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Prueba Notif","email":"delivered@resend.dev","password":"Clave123*"}'
```

4. Abre `http://localhost:15672` (`guest` / `guest`) → **Exchanges**. Debe existir `skillbridge.events`. Si el consumidor de Kevin todavía no está mergeado, los mensajes se pierden porque no hay cola enlazada. Es normal: para verlos, crea una cola temporal y enlázala con `user.#` y `enrollment.#`.
5. Inscríbete en un servicio desde el frontend → `Evento publicado: type=enrollment.created`.

---

## Paso 15: Subir tu trabajo (TÚ ejecutas estos comandos)

```bash
# Ver qué cambiaste
git status
git diff --stat

# Agregar sólo los archivos del backend de esta tarea
git add backend/src/main/java/com/riwi/skillbridge/domain/event \
        backend/src/main/java/com/riwi/skillbridge/application/port/out/UserEventPublisherPort.java \
        backend/src/main/java/com/riwi/skillbridge/application/service/UserEventEmitter.java \
        backend/src/main/java/com/riwi/skillbridge/application/service/UserAccountService.java \
        backend/src/main/java/com/riwi/skillbridge/application/service/AdminUserService.java \
        backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java \
        backend/src/main/java/com/riwi/skillbridge/application/service/ServiceEnrollmentService.java \
        backend/src/main/java/com/riwi/skillbridge/application/port/in/useraccount \
        backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging \
        backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java \
        backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/UserAccountController.java \
        backend/src/main/java/com/riwi/skillbridge/domain/exception/AccountAccessDeniedException.java \
        backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java \
        backend/src/test

# Commit (Conventional Commits en español, como el resto del repo)
git commit -m "feat(notificaciones): publicar eventos de cuenta y de reserva en RabbitMQ (skillbridge.events) (KAN-59)

- fix(seguridad): solo ADMIN o el propio usuario pueden cambiar estado o eliminar la cuenta (403)
- fix(seguridad): no se puede desactivar al administrador
- fix(auth): rechazar login de cuentas INACTIVE"

# Subir la rama
git push -u origin feature/notificaciones-java

# Abrir el PR hacia la rama de integración (requiere GitHub CLI autenticado: gh auth login)
gh pr create --base feature/notificaciones --head feature/notificaciones-java \
  --title "feat(notificaciones): productor de eventos de usuario (Java)" \
  --body "HU-NOT00 (KAN-59): publica user.registered, user.created, user.status.changed, user.role.changed, user.deleted y enrollment.created en skillbridge.events según docs/notifications/CONTRACT.md. Incluye correcciones de seguridad (403, ADMIN no desactivable, login INACTIVE) y pruebas."
```

---

## Definition of Done (HU-NOT00 / KAN-59)

- [ ] `mvn -B clean verify` en verde.
- [ ] Los 6 eventos se publican **sólo** cuando hay un cambio real (incluye `enrollment.created` al inscribirse).
- [ ] El JSON coincide con el contrato (pasa `message_hasSameJsonShapeAsContractFixture`).
- [ ] Con RabbitMQ apagado, registro, cambio de rol y borrado **siguen funcionando** (sólo aparece un WARN).
- [ ] Ningún log imprime emails ni contraseñas.
- [ ] Paso 13: un usuario no puede desactivar ni borrar a otro (403), el ADMIN no se puede desactivar y el login de una cuenta INACTIVE responde 401 con un mensaje claro.
- [ ] PR abierto hacia `feature/notificaciones`.
- [ ] Entrada en `MEMORIA.md` (regla 4 de `AGENTS.md`).

---

## Prompt para otra IA (si necesitas ayuda)

```text
Trabajo en SkillBridge AI: backend Java 21 + Spring Boot 3.5 con Arquitectura Hexagonal
(domain / application con puertos in/out / infrastructure con adaptadores). Reglas de AGENTS.md:
el dominio no conoce Spring/RabbitMQ; toda integración externa va por un puerto en application/port/out
con su adaptador en infrastructure; cambios de esquema sólo con migración Flyway nueva; registrar
la tarea en MEMORIA.md. NO hagas git commit ni push.

Tarea: publicar eventos de cuenta de usuario en RabbitMQ (exchange topic "skillbridge.events") para que
un microservicio NestJS envíe correos. El contrato está en docs/notifications/CONTRACT.md y los ejemplos en
docs/notifications/contract/*.json. Sigo la guía docs/notifications/GUIA-DYLAN-HU-NOT00-JAVA.md.
Estoy en el Paso <N> y tengo este problema: <pega el error / código>.
Explícame la causa y dame el bloque de código corregido con una explicación de cada parte.
```
