package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.MeetingAccess;
import com.riwi.skillbridge.application.port.in.meeting.JoinMeetingUseCase;
import com.riwi.skillbridge.application.port.in.meeting.ManageMeetingUseCase;
import com.riwi.skillbridge.application.port.in.meeting.ProcessLiveKitWebhookUseCase;
import com.riwi.skillbridge.application.port.in.meeting.RetrieveMeetingUseCase;
import com.riwi.skillbridge.application.port.out.LiveKitPort;
import com.riwi.skillbridge.application.port.out.MeetingHistoryPort;
import com.riwi.skillbridge.application.port.out.MeetingParticipantPort;
import com.riwi.skillbridge.application.port.out.MeetingPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.enums.Frequency;
import com.riwi.skillbridge.domain.enums.MeetingEventType;
import com.riwi.skillbridge.domain.enums.MeetingParticipantStatus;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.domain.enums.MeetingStatus;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.LiveKitIntegrationException;
import com.riwi.skillbridge.domain.model.Meeting;
import com.riwi.skillbridge.domain.model.MeetingHistoryEntry;
import com.riwi.skillbridge.domain.model.MeetingParticipant;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida de las reuniones virtuales con LiveKit. Centraliza las reglas:
 *
 *  Regla 1 — Solo el proveedor propietario inicia/finaliza/cancela/extiende.
 *  Regla 2 — Solo inscritos activos o el proveedor acceden; token por identidad.
 *  Regla 3 — Ventana de inicio: [scheduledStart, scheduledStart + 30 min].
 *  Regla 4 — Duración del servicio + extensión única de 20 min; límite estricto.
 *  Regla 5 — Diferencia CANCELADA (con motivo y responsable) de NO_REALIZADA.
 *  Regla 6 — Modalidad unilateral: permisos del token según el rol.
 *
 * Transiciones de la reunión (única representación, sin estados duplicados):
 *  (nueva) → PROGRAMADA → EN_CURSO → FINALIZADA
 *  PROGRAMADA → CANCELADA | NO_REALIZADA
 *  EN_CURSO   → CANCELADA | FINALIZADA (por límite, evento CIERRE_POR_LIMITE)
 */
@Service
public class MeetingService implements ManageMeetingUseCase, JoinMeetingUseCase, RetrieveMeetingUseCase, ProcessLiveKitWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);

    /** Regla 3: minutos posteriores al inicio programado durante los que el proveedor puede iniciar. */
    public static final int START_WINDOW_MINUTES = 30;
    /** Regla 4: extensión máxima concedible, una sola vez, sin renovación. */
    public static final int DEFAULT_EXTENSION_MINUTES = 20;

    private final MeetingPort meetingPort;
    private final MeetingParticipantPort participantPort;
    private final MeetingHistoryPort historyPort;
    private final LiveKitPort liveKitPort;
    private final OfferingPort offeringPort;
    private final ServiceSchedulePort schedulePort;
    private final ServiceEnrollmentPort enrollmentPort;
    private final UserAccountPort userAccountPort;
    private final Clock clock;

    @Autowired
    public MeetingService(MeetingPort meetingPort, MeetingParticipantPort participantPort,
                          MeetingHistoryPort historyPort, LiveKitPort liveKitPort,
                          OfferingPort offeringPort, ServiceSchedulePort schedulePort,
                          ServiceEnrollmentPort enrollmentPort, UserAccountPort userAccountPort) {
        this(meetingPort, participantPort, historyPort, liveKitPort, offeringPort,
            schedulePort, enrollmentPort, userAccountPort, Clock.systemUTC());
    }

    MeetingService(MeetingPort meetingPort, MeetingParticipantPort participantPort,
                   MeetingHistoryPort historyPort, LiveKitPort liveKitPort,
                   OfferingPort offeringPort, ServiceSchedulePort schedulePort,
                   ServiceEnrollmentPort enrollmentPort, Clock clock) {
        this(meetingPort, participantPort, historyPort, liveKitPort, offeringPort,
            schedulePort, enrollmentPort, null, clock);
    }

    MeetingService(MeetingPort meetingPort, MeetingParticipantPort participantPort,
                   MeetingHistoryPort historyPort, LiveKitPort liveKitPort,
                   OfferingPort offeringPort, ServiceSchedulePort schedulePort,
                   ServiceEnrollmentPort enrollmentPort, UserAccountPort userAccountPort, Clock clock) {
        this.meetingPort = meetingPort;
        this.participantPort = participantPort;
        this.historyPort = historyPort;
        this.liveKitPort = liveKitPort;
        this.offeringPort = offeringPort;
        this.schedulePort = schedulePort;
        this.enrollmentPort = enrollmentPort;
        this.userAccountPort = userAccountPort;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Regla 1

    @Override
    public MeetingAccess startMeeting(UUID providerId, UUID serviceId) {
        Offering service = assertOwnsService(providerId, serviceId);

        List<ServiceSchedule> schedules = schedulePort.findByServiceId(serviceId);
        if (schedules.isEmpty())
            throw new BusinessRuleException("El servicio no tiene horario definido");

        OffsetDateTime now = now();
        SessionSlot slot = resolveCurrentSlot(schedules, now)
            .orElseThrow(() -> new BusinessRuleException(
                "No hay una sesión programada para iniciar en este momento"));

        // Idempotencia: reutiliza la reunión de la sesión (única por servicio+horario+fecha)
        Optional<Meeting> existing = meetingPort
            .findByServiceIdAndScheduleIdAndMeetingDate(serviceId, slot.schedule().id(), slot.date());
        if (existing.isEmpty() && now.isAfter(startWindowEnd(slot.scheduledStart())))
            throw new BusinessRuleException("La ventana de inicio de la sesión ya expiró");
        Meeting meeting = existing.orElseGet(() -> createProgrammedMeeting(service, slot));

        if (meeting.status() == MeetingStatus.EN_CURSO) {
            // Reintento/idempotencia: no reinicia, solo reemite el token del proveedor
            return accessFor(meeting, providerId, MeetingRole.PROVIDER, participantName(providerId));
        }
        requireTransition(meeting, MeetingStatus.EN_CURSO);
        validateStartWindow(meeting, now);

        // Sala LiveKit: creada una sola vez y reutilizada en reintentos
        Meeting withRoom = ensureRoom(meeting);
        OffsetDateTime maxEnd = now.plusMinutes(withRoom.durationMinutes());

        Meeting started = save(withRoom, MeetingStatus.EN_CURSO,
            now, null, maxEnd, null, null);
        recordEvent(started, providerId, MeetingEventType.REUNION_INICIADA, "OK", null, null);
        registerParticipant(started, providerId, MeetingRole.PROVIDER, MeetingParticipantStatus.AUTORIZADO);

        return accessFor(started, providerId, MeetingRole.PROVIDER, participantName(providerId));
    }

    @Override
    public Meeting finishMeeting(UUID userId, UUID meetingId) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));
        boolean isAdmin = userAccountPort != null && userAccountPort.findById(userId)
            .map(u -> u.role() == Role.ADMIN).orElse(false);
        if (!isAdmin) {
            assertOwnsService(userId, meeting.serviceId());
        }
        if (meeting.status() != MeetingStatus.EN_CURSO)
            throw new BusinessRuleException("Solo se pueden finalizar reuniones en curso");

        closeRoomQuietly(meeting.livekitRoom());
        Meeting finished = closeMeeting(meeting, MeetingStatus.FINALIZADA, now(),
            MeetingEventType.REUNION_FINALIZADA, null);
        closeParticipants(finished.id());
        return finished;
    }

    @Override
    public MeetingAccess createTestMeeting(UUID adminUserId, UUID serviceId) {
        Offering service = offeringPort.findById(serviceId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + serviceId));

        if (userAccountPort != null) {
            UserAccount user = userAccountPort.findById(adminUserId)
                .orElseThrow(() -> new BusinessRuleException("Usuario administrador no encontrado"));
            if (user.role() != Role.ADMIN) {
                throw new BusinessRuleException("Solo un administrador puede crear salas de prueba");
            }
        }

        OffsetDateTime now = now();
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        List<ServiceSchedule> schedules = schedulePort.findByServiceId(serviceId);
        ServiceSchedule schedule;
        if (schedules.isEmpty()) {
            schedule = schedulePort.save(new ServiceSchedule(UUID.randomUUID(), serviceId,
                now.getDayOfWeek(), 60, Frequency.WEEKLY, 1, today, time, time.plusHours(1)));
        } else {
            schedule = schedules.get(0);
        }

        Optional<Meeting> active = meetingPort.findByServiceId(serviceId).stream()
            .filter(m -> m.status() == MeetingStatus.EN_CURSO)
            .findFirst();

        Meeting meeting;
        if (active.isPresent()) {
            meeting = active.get();
        } else {
            UUID meetingId = UUID.randomUUID();
            String roomName = "test-room-" + meetingId;
            liveKitPort.createRoom(roomName);

            meeting = new Meeting(
                meetingId,
                serviceId,
                schedule.id(),
                today,
                time,
                60,
                MeetingStatus.EN_CURSO,
                roomName,
                true,
                now,
                now,
                null,
                now.plusHours(2),
                20,
                false,
                null,
                null,
                now,
                now
            );
            meeting = meetingPort.save(meeting);
            recordEvent(meeting, adminUserId, MeetingEventType.REUNION_INICIADA, "OK", "SALA_TEST_ADMIN", null);
        }

        registerParticipant(meeting, adminUserId, MeetingRole.ADMIN, MeetingParticipantStatus.AUTORIZADO);
        return accessFor(meeting, adminUserId, MeetingRole.ADMIN, participantName(adminUserId));
    }

    @Override
    public Meeting cancelMeeting(UUID providerId, UUID meetingId, String reason) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));
        assertOwnsService(providerId, meeting.serviceId());
        if (meeting.status() != MeetingStatus.PROGRAMADA && meeting.status() != MeetingStatus.EN_CURSO)
            throw new BusinessRuleException("La reunión ya no puede cancelarse (estado: " + meeting.status() + ")");

        closeRoomQuietly(meeting.livekitRoom());
        OffsetDateTime end = now();
        Meeting cancelled = save(meeting, MeetingStatus.CANCELADA,
            meeting.actualStart(), end, meeting.maxEnd(),
            reason, providerId);
        recordEvent(cancelled, providerId, MeetingEventType.REUNION_CANCELADA, "OK",
            reason, null);
        closeParticipants(cancelled.id());
        return cancelled;
    }

    @Override
    public Meeting extendMeeting(UUID providerId, UUID meetingId) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));
        assertOwnsService(providerId, meeting.serviceId());
        if (meeting.status() != MeetingStatus.EN_CURSO)
            throw new BusinessRuleException("Solo se pueden extender reuniones en curso");
        if (meeting.extensionUsed())
            throw new BusinessRuleException("La extensión máxima ya fue utilizada y no puede renovarse");

        OffsetDateTime newMaxEnd = meeting.actualStart()
            .plusMinutes(meeting.durationMinutes() + meeting.extensionMinutes());
        Meeting extended = meetingPort.save(new Meeting(meeting.id(), meeting.serviceId(), meeting.scheduleId(),
            meeting.meetingDate(), meeting.startTime(), meeting.durationMinutes(),
            MeetingStatus.EN_CURSO, meeting.livekitRoom(), meeting.roomCreated(),
            meeting.scheduledStart(), meeting.actualStart(), meeting.actualEnd(), newMaxEnd,
            meeting.extensionMinutes(), true, meeting.cancellationReason(), meeting.cancelledBy(),
            meeting.createdAt(), now()));
        recordEvent(extended, providerId, MeetingEventType.EXTENSION_CONCEDIDA, "OK",
            "Extensión de " + meeting.extensionMinutes() + " minutos concedida",
            "{\"extraMinutes\":" + meeting.extensionMinutes() + "}");
        return extended;
    }

    // ---------------------------------------------------------------- Regla 2

    @Override
    public MeetingAccess join(UUID userId, UUID meetingId) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));

        // Regla 4: límite de tiempo validado en backend, no en el navegador
        OffsetDateTime now = now();
        if (meeting.status() == MeetingStatus.EN_CURSO && meeting.maxEnd() != null && now.isAfter(meeting.maxEnd())) {
            forceCloseByLimit(meeting);
            deny(meeting, userId, "La reunión alcanzó su límite máximo de tiempo");
        }

        Offering service = offeringPort.findById(meeting.serviceId())
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + meeting.serviceId()));

        MeetingRole role;
        boolean isAdmin = userAccountPort != null && userAccountPort.findById(userId)
            .map(u -> u.role() == Role.ADMIN).orElse(false);

        if (isAdmin) {
            role = MeetingRole.ADMIN;
        } else if (service.createdBy().equals(userId)) {
            role = MeetingRole.PROVIDER;
        } else {
            Optional<ServiceEnrollment> enrollment =
                enrollmentPort.findByUserIdAndServiceId(userId, meeting.serviceId())
                    .filter(e -> e.status() == EnrollmentStatus.ACTIVE);
            if (enrollment.isEmpty()) {
                deny(meeting, userId, "No tienes una inscripción activa en este servicio");
                throw new IllegalStateException("unreachable");
            }
            role = MeetingRole.PARTICIPANT;
        }

        if (meeting.status() != MeetingStatus.EN_CURSO) {
            String message = switch (meeting.status()) {
                case PROGRAMADA -> "La reunión aún no ha iniciado";
                case CANCELADA -> "La reunión fue cancelada";
                case NO_REALIZADA -> "La reunión no se realizó";
                default -> "La reunión ya finalizó";
            };
            deny(meeting, userId, message);
            throw new IllegalStateException("unreachable");
        }

        // Registro del ingreso (el paso a EN_SESION lo confirma el webhook de LiveKit).
        // Se reutiliza la fila existente aunque hubiera intentos denegados previos.
        OffsetDateTime joinedAt = now;
        Optional<MeetingParticipant> existingRow = participantPort.findByMeetingIdAndUserId(meeting.id(), userId);
        if (existingRow.isPresent()) {
            MeetingParticipant p = existingRow.get();
            if (p.status() != MeetingParticipantStatus.EN_SESION) {
                participantPort.save(updateParticipant(p,
                    MeetingParticipantStatus.INGRESO_REGISTRADO, joinedAt, null));
            }
        } else {
            registerParticipant(meeting, userId, role, MeetingParticipantStatus.INGRESO_REGISTRADO);
        }
        recordEvent(meeting, userId, MeetingEventType.PARTICIPANTE_INGRESO, "TOKEN_EMITIDO",
            null, "{\"role\":\"" + role + "\",\"canPublish\":" + (role == MeetingRole.PROVIDER || role == MeetingRole.ADMIN) + "}");

        return accessFor(meeting, userId, role, participantName(userId));
    }

    // ------------------------------------------------------------ Consultas

    @Override
    public Optional<Meeting> findById(UUID meetingId) {
        return meetingPort.findById(meetingId);
    }

    @Override
    public List<Meeting> findByService(UUID userId, UUID serviceId) {
        assertCanView(userId, serviceId);
        return meetingPort.findByServiceId(serviceId);
    }

    @Override
    public List<MeetingParticipant> findParticipants(UUID userId, UUID meetingId) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));
        assertCanView(userId, meeting.serviceId());
        return participantPort.findByMeetingId(meetingId);
    }

    @Override
    public List<MeetingHistoryEntry> findHistory(UUID userId, UUID meetingId) {
        Meeting meeting = meetingPort.findById(meetingId)
            .orElseThrow(() -> new DomainNotFoundException("Reunión no encontrada con id: " + meetingId));
        assertCanView(userId, meeting.serviceId());
        return historyPort.findByMeetingId(meetingId);
    }

    // ---------------------------------------------------- Tareas programadas

    /**
     * Marca como NO_REALIZADA las reuniones que superaron su ventana de inicio
     * sin haber comenzado (Regla 5). Devuelve cuántas marcó.
     */
    public int markExpiredMeetings() {
        OffsetDateTime now = now();
        int marked = 0;
        for (Meeting meeting : meetingPort.findByStatus(MeetingStatus.PROGRAMADA)) {
            if (now.isAfter(startWindowEnd(meeting))) {
                Meeting noRealizada = save(meeting, MeetingStatus.NO_REALIZADA,
                    null, null, null, null, null);
                recordEvent(noRealizada, null, MeetingEventType.REUNION_NO_REALIZADA,
                    "VENTANA_EXPIRADA",
                    "La reunión no se inició dentro de la ventana permitida", null);
                closeParticipants(noRealizada.id());
                marked++;
            }
        }
        return marked;
    }

    /**
     * Cierra reuniones en curso que alcanzaron su límite máximo de tiempo
     * (Regla 4), revocando el acceso al cerrar la sala. Devuelve cuántas cerró.
     */
    public int forceCloseTimedOutMeetings() {
        OffsetDateTime now = now();
        int closed = 0;
        for (Meeting meeting : meetingPort.findByStatus(MeetingStatus.EN_CURSO)) {
            if (meeting.maxEnd() != null && now.isAfter(meeting.maxEnd())) {
                forceCloseByLimit(meeting);
                closed++;
            }
        }
        return closed;
    }

    /** Cierre forzado por límite de tiempo. También lo invoca el chequeo de acceso. */
    public void forceCloseByLimit(Meeting meeting) {
        closeRoomQuietly(meeting.livekitRoom());
        Meeting closed = closeMeeting(meeting, MeetingStatus.FINALIZADA, now(),
            MeetingEventType.CIERRE_POR_LIMITE,
            "Se alcanzó el límite máximo de tiempo (duración + extensión)");
        closeParticipants(closed.id());
    }

    // -------------------------------------------------- Webhooks LiveKit (HU-29.3)

    @Override
    public void handleRoomStarted(UUID meetingId, OffsetDateTime timestamp) {
        Meeting meeting = meetingPort.findById(meetingId).orElse(null);
        if (meeting == null) {
            log.warn("Webhook room_started para reunión inexistente: {}", meetingId);
            return;
        }
        OffsetDateTime effectiveStart = timestamp != null ? timestamp : now();
        if (meeting.status() == MeetingStatus.PROGRAMADA) {
            OffsetDateTime maxEnd = effectiveStart.plusMinutes(meeting.durationMinutes());
            Meeting started = save(meeting, MeetingStatus.EN_CURSO,
                effectiveStart, null, maxEnd, null, null);
            recordEvent(started, null, MeetingEventType.REUNION_INICIADA, "CONFIRMADO_LIVEKIT", null, null);
        } else if (meeting.status() == MeetingStatus.EN_CURSO) {
            if (meeting.actualStart() == null) {
                OffsetDateTime maxEnd = effectiveStart.plusMinutes(meeting.durationMinutes());
                save(meeting, MeetingStatus.EN_CURSO, effectiveStart, null, maxEnd, null, null);
            }
            recordEvent(meeting, null, MeetingEventType.REUNION_INICIADA, "CONFIRMADO_LIVEKIT_IDEMPOTENTE", null, null);
        } else {
            log.info("Webhook room_started ignorado por estado actual de reunión {}: {}", meetingId, meeting.status());
        }
    }

    @Override
    public void handleRoomFinished(UUID meetingId, OffsetDateTime timestamp) {
        Meeting meeting = meetingPort.findById(meetingId).orElse(null);
        if (meeting == null) {
            log.warn("Webhook room_finished para reunión inexistente: {}", meetingId);
            return;
        }
        OffsetDateTime effectiveEnd = timestamp != null ? timestamp : now();
        if (meeting.status() == MeetingStatus.EN_CURSO || meeting.status() == MeetingStatus.PROGRAMADA) {
            Meeting finished = closeMeeting(meeting, MeetingStatus.FINALIZADA, effectiveEnd,
                MeetingEventType.REUNION_FINALIZADA, "Finalización confirmada por LiveKit");
            closeParticipants(finished.id(), effectiveEnd);
        } else {
            log.info("Webhook room_finished para reunión {} en estado {} (idempotente)", meetingId, meeting.status());
        }
    }

    @Override
    public void handleParticipantJoined(UUID meetingId, UUID userId, OffsetDateTime timestamp) {
        Meeting meeting = meetingPort.findById(meetingId).orElse(null);
        if (meeting == null) {
            log.warn("Webhook participant_joined para reunión inexistente: {}", meetingId);
            return;
        }
        OffsetDateTime effectiveTime = timestamp != null ? timestamp : now();
        participantPort.findByMeetingIdAndUserId(meetingId, userId).ifPresentOrElse(p -> {
            participantPort.save(updateParticipant(p, MeetingParticipantStatus.EN_SESION, effectiveTime, null));
            recordEvent(meeting, userId, MeetingEventType.PARTICIPANTE_INGRESO, "CONFIRMADO_LIVEKIT", null, null);
        }, () -> {
            MeetingParticipant newP = new MeetingParticipant(UUID.randomUUID(), meetingId, userId,
                MeetingRole.PARTICIPANT, MeetingParticipantStatus.EN_SESION, effectiveTime, null, null, effectiveTime, effectiveTime);
            participantPort.save(newP);
            recordEvent(meeting, userId, MeetingEventType.PARTICIPANTE_INGRESO, "CONFIRMADO_LIVEKIT", null, null);
        });
    }

    @Override
    public void handleParticipantLeft(UUID meetingId, UUID userId, OffsetDateTime timestamp) {
        Meeting meeting = meetingPort.findById(meetingId).orElse(null);
        if (meeting == null) {
            log.warn("Webhook participant_left para reunión inexistente: {}", meetingId);
            return;
        }
        OffsetDateTime effectiveTime = timestamp != null ? timestamp : now();
        participantPort.findByMeetingIdAndUserId(meetingId, userId).ifPresent(p -> {
            participantPort.save(updateParticipant(p, MeetingParticipantStatus.SALIDA_REGISTRADA, p.joinedAt(), effectiveTime));
            recordEvent(meeting, userId, MeetingEventType.PARTICIPANTE_SALIO, "OK", null, null);
        });
    }

    /** Registro de ingreso/salida delegado (compatibilidad). */
    public void registerWebhookEvent(UUID meetingId, UUID userId, MeetingEventType eventType) {
        if (eventType == MeetingEventType.PARTICIPANTE_INGRESO) {
            handleParticipantJoined(meetingId, userId, null);
        } else if (eventType == MeetingEventType.PARTICIPANTE_SALIO) {
            handleParticipantLeft(meetingId, userId, null);
        }
    }

    // ------------------------------------------------------------- Internos

    private record SessionSlot(ServiceSchedule schedule, LocalDate date,
                               OffsetDateTime scheduledStart) {}

    /** Sesión actual: la de inicio programado más reciente que ya llegó (hoy o antes). */
    private Optional<SessionSlot> resolveCurrentSlot(List<ServiceSchedule> schedules, OffsetDateTime now) {
        LocalDate today = LocalDate.now(clock);
        return schedules.stream()
            .flatMap(s -> MeetingScheduleResolver.nextOccurrenceOnOrAfter(s, today).stream()
                .map(d -> new SessionSlot(s, d, MeetingScheduleResolver.scheduledStartAt(s, d))))
            .filter(slot -> !slot.scheduledStart().isAfter(now))
            .max(Comparator.comparing(SessionSlot::scheduledStart));
    }

    private Meeting createProgrammedMeeting(Offering service, SessionSlot slot) {
        OffsetDateTime now = now();
        UUID meetingId = UUID.randomUUID();
        // La sala se identifica con el UUID de la reunión: correlación directa con los webhooks
        Meeting meeting = new Meeting(meetingId, service.id(), slot.schedule().id(),
            slot.date(), slot.schedule().startTime(), slot.schedule().sessionDuration(),
            MeetingStatus.PROGRAMADA, meetingId.toString(), false,
            slot.scheduledStart(), null, null, null,
            DEFAULT_EXTENSION_MINUTES, false, null, null, now, now);
        try {
            return meetingPort.save(meeting);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Concurrencia: otra petición creó la misma sesión; reutilizarla (idempotencia)
            return meetingPort
                .findByServiceIdAndScheduleIdAndMeetingDate(service.id(), slot.schedule().id(), slot.date())
                .orElseThrow(() -> e);
        } finally {
            recordEvent(meeting, service.createdBy(), MeetingEventType.REUNION_PROGRAMADA, "OK", null,
                "{\"scheduledStart\":\"" + slot.scheduledStart() + "\"}");
        }
    }

    private Meeting ensureRoom(Meeting meeting) {
        if (meeting.roomCreated()) return meeting;
        try {
            liveKitPort.createRoom(meeting.livekitRoom());
        } catch (LiveKitIntegrationException e) {
            recordEvent(meeting, null, MeetingEventType.ERROR_LIVEKIT, "SALA_NO_CREADA",
                e.getMessage(), null);
            throw e;
        }
        Meeting withRoom = new Meeting(meeting.id(), meeting.serviceId(), meeting.scheduleId(),
            meeting.meetingDate(), meeting.startTime(), meeting.durationMinutes(),
            meeting.status(), meeting.livekitRoom(), true,
            meeting.scheduledStart(), meeting.actualStart(), meeting.actualEnd(), meeting.maxEnd(),
            meeting.extensionMinutes(), meeting.extensionUsed(),
            meeting.cancellationReason(), meeting.cancelledBy(), meeting.createdAt(), now());
        Meeting saved = meetingPort.save(withRoom);
        recordEvent(saved, null, MeetingEventType.SALA_CREADA, "OK", null,
            "{\"room\":\"" + meeting.livekitRoom() + "\"}");
        return saved;
    }

    private MeetingAccess accessFor(Meeting meeting, UUID userId, MeetingRole role, String name) {
        OffsetDateTime expiresAt = meeting.maxEnd() != null ? meeting.maxEnd()
            : now().plusMinutes(meeting.durationMinutes());
        String token = liveKitPort.createAccessToken(meeting.livekitRoom(),
            userId.toString(), name, role, expiresAt);
        return new MeetingAccess(meeting, liveKitPort.serverUrl(), token, role, expiresAt);
    }

    private void deny(Meeting meeting, UUID userId, String reason) {
        OffsetDateTime now = now();
        participantPort.findByMeetingIdAndUserId(meeting.id(), userId)
            .ifPresentOrElse(
                p -> participantPort.save(new MeetingParticipant(p.id(), p.meetingId(), p.userId(),
                    p.role(), MeetingParticipantStatus.ACCESO_DENEGADO, p.joinedAt(), p.leftAt(),
                    reason, p.createdAt(), now)),
                () -> participantPort.save(new MeetingParticipant(UUID.randomUUID(), meeting.id(),
                    userId, MeetingRole.PARTICIPANT, MeetingParticipantStatus.ACCESO_DENEGADO,
                    null, null, reason, now, now)));
        recordEvent(meeting, userId, MeetingEventType.ACCESO_RECHAZADO, "PERMISO_INSUFICIENTE", reason, null);
        throw new BusinessRuleException(reason);
    }

    private void closeParticipants(UUID meetingId) {
        closeParticipants(meetingId, now());
    }

    private void closeParticipants(UUID meetingId, OffsetDateTime effectiveTime) {
        OffsetDateTime exitTime = effectiveTime != null ? effectiveTime : now();
        for (MeetingParticipant p : participantPort.findByMeetingId(meetingId)) {
            MeetingParticipantStatus target = switch (p.status()) {
                case EN_SESION, INGRESO_REGISTRADO -> MeetingParticipantStatus.SALIDA_REGISTRADA;
                case AUTORIZADO -> MeetingParticipantStatus.NO_ASISTIO;
                default -> p.status();
            };
            if (target == p.status()) continue;
            OffsetDateTime left = target == MeetingParticipantStatus.SALIDA_REGISTRADA ? exitTime : p.leftAt();
            participantPort.save(updateParticipant(p, target, p.joinedAt(), left));
        }
    }

    private Meeting closeMeeting(Meeting meeting, MeetingStatus target, OffsetDateTime end,
                                 MeetingEventType event, String reason) {
        Meeting closed = save(meeting, target, meeting.actualStart(), end, meeting.maxEnd(),
            meeting.cancellationReason(), meeting.cancelledBy());
        recordEvent(closed, null, event, "OK", reason, null);
        return closed;
    }

    private void closeRoomQuietly(String roomName) {
        try {
            liveKitPort.deleteRoom(roomName);
        } catch (LiveKitIntegrationException e) {
            log.warn("No se pudo cerrar la sala {}: {}", roomName, e.getMessage());
        }
    }

    private void validateStartWindow(Meeting meeting, OffsetDateTime now) {
        if (now.isBefore(meeting.scheduledStart()))
            throw new BusinessRuleException("La reunión aún no puede iniciar (inicio programado: "
                + meeting.scheduledStart() + " UTC)");
        if (now.isAfter(startWindowEnd(meeting))) {
            // Regla 5: se registra como no realizada y no admite inicio ordinario
            Meeting noRealizada = save(meeting, MeetingStatus.NO_REALIZADA, null, null, null, null, null);
            recordEvent(noRealizada, null, MeetingEventType.REUNION_NO_REALIZADA, "VENTANA_EXPIRADA",
                "La ventana de inicio expiró sin que la reunión comenzara", null);
            throw new BusinessRuleException("La ventana de inicio expiró; la reunión quedó registrada como no realizada");
        }
    }

    private OffsetDateTime startWindowEnd(Meeting meeting) {
        return startWindowEnd(meeting.scheduledStart());
    }

    private OffsetDateTime startWindowEnd(OffsetDateTime scheduledStart) {
        return scheduledStart.plus(Duration.ofMinutes(START_WINDOW_MINUTES));
    }

    private void requireTransition(Meeting meeting, MeetingStatus target) {
        boolean valid = switch (target) {
            case EN_CURSO -> meeting.status() == MeetingStatus.PROGRAMADA;
            case FINALIZADA, CANCELADA -> meeting.status() == MeetingStatus.EN_CURSO
                || meeting.status() == MeetingStatus.PROGRAMADA;
            default -> false;
        };
        if (!valid)
            throw new BusinessRuleException("Transición no permitida: " + meeting.status() + " → " + target);
    }

    private Offering assertOwnsService(UUID providerId, UUID serviceId) {
        Offering service = offeringPort.findById(serviceId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + serviceId));
        if (!service.createdBy().equals(providerId))
            throw new BusinessRuleException("No puedes administrar servicios de otro proveedor");
        return service;
    }

    private void assertCanView(UUID userId, UUID serviceId) {
        if (userAccountPort != null && userAccountPort.findById(userId).map(u -> u.role() == Role.ADMIN).orElse(false)) {
            return;
        }
        Offering service = offeringPort.findById(serviceId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + serviceId));
        if (service.createdBy().equals(userId)) return;
        boolean enrolled = enrollmentPort.findByUserIdAndServiceId(userId, serviceId)
            .filter(e -> e.status() == EnrollmentStatus.ACTIVE)
            .isPresent();
        if (!enrolled)
            throw new BusinessRuleException("No tienes acceso a las reuniones de este servicio");
    }

    private Meeting save(Meeting m, MeetingStatus status, OffsetDateTime actualStart,
                         OffsetDateTime actualEnd, OffsetDateTime maxEnd,
                         String cancellationReason, UUID cancelledBy) {
        return meetingPort.save(new Meeting(m.id(), m.serviceId(), m.scheduleId(), m.meetingDate(),
            m.startTime(), m.durationMinutes(), status, m.livekitRoom(), m.roomCreated(),
            m.scheduledStart(), actualStart, actualEnd, maxEnd,
            m.extensionMinutes(), m.extensionUsed(), cancellationReason, cancelledBy,
            m.createdAt(), now()));
    }

    private MeetingParticipant registerParticipant(Meeting meeting, UUID userId, MeetingRole role,
                                                   MeetingParticipantStatus status) {
        OffsetDateTime now = now();
        return participantPort.findByMeetingIdAndUserId(meeting.id(), userId)
            .map(existing -> participantPort.save(new MeetingParticipant(
                existing.id(),
                existing.meetingId(),
                existing.userId(),
                role,
                status,
                status == MeetingParticipantStatus.INGRESO_REGISTRADO ? (existing.joinedAt() != null ? existing.joinedAt() : now) : existing.joinedAt(),
                existing.leftAt(),
                existing.denialReason(),
                existing.createdAt(),
                now
            )))
            .orElseGet(() -> participantPort.save(new MeetingParticipant(
                UUID.randomUUID(),
                meeting.id(),
                userId,
                role,
                status,
                status == MeetingParticipantStatus.INGRESO_REGISTRADO ? now : null,
                null,
                null,
                now,
                now
            )));
    }

    private MeetingParticipant updateParticipant(MeetingParticipant p, MeetingParticipantStatus status,
                                                 OffsetDateTime joinedAt, OffsetDateTime leftAt) {
        return new MeetingParticipant(p.id(), p.meetingId(), p.userId(), p.role(), status,
            joinedAt, leftAt, p.denialReason(), p.createdAt(), now());
    }

    private void recordEvent(Meeting meeting, UUID userId, MeetingEventType type,
                             String result, String reason, String metadata) {
        try {
            historyPort.save(new MeetingHistoryEntry(UUID.randomUUID(), meeting.id(), userId,
                type, result, reason, metadata, now()));
        } catch (RuntimeException e) {
            // Trazabilidad best-effort: un fallo de persistencia del historial no debe
            // ocultar el estado real de la operación; queda el log como respaldo.
            log.error("No se pudo persistir el evento {} de la reunión {}: {}",
                type, meeting.id(), e.getMessage());
        }
    }

    private String participantName(UUID userId) {
        if (userAccountPort != null) {
            return userAccountPort.findById(userId)
                .map(u -> (u.name() != null && !u.name().isBlank()) ? u.name() : userId.toString())
                .orElse(userId.toString());
        }
        return userId.toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    }
}
