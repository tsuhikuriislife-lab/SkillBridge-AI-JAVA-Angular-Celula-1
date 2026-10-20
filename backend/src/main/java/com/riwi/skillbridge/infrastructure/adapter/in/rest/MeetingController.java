package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.MeetingAccess;
import com.riwi.skillbridge.application.port.in.meeting.JoinMeetingUseCase;
import com.riwi.skillbridge.application.port.in.meeting.ManageMeetingUseCase;
import com.riwi.skillbridge.application.port.in.meeting.RetrieveMeetingUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Meeting;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CancelMeetingRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.MeetingHistoryOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.MeetingOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.MeetingParticipantOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.MeetingTokenOut;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API de reuniones virtuales con LiveKit. Toda operación valida la identidad
 * autenticada contra las relaciones del dominio (servicio → proveedor,
 * inscripción → participante); nunca confía solo en los identificadores del cliente.
 */
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final ManageMeetingUseCase manage;
    private final JoinMeetingUseCase join;
    private final RetrieveMeetingUseCase retrieve;
    private final UserAccountPort users;

    public MeetingController(ManageMeetingUseCase manage, JoinMeetingUseCase join,
                             RetrieveMeetingUseCase retrieve, UserAccountPort users) {
        this.manage = manage; this.join = join; this.retrieve = retrieve; this.users = users;
    }

    /** Regla 1: solo el proveedor propietario inicia, dentro de la ventana de inicio. */
    @PostMapping("/service/{serviceId}/start")
    @PreAuthorize("hasRole('PROVIDER')")
    public MeetingTokenOut start(@AuthenticationPrincipal UserDetails user,
                                 @PathVariable UUID serviceId) {
        return toTokenOut(manage.startMeeting(currentUserId(user), serviceId));
    }

    /** Regla 2: solo inscritos activos o el proveedor; el token se genera en backend. */
    @PostMapping("/{meetingId}/join")
    public MeetingTokenOut join(@AuthenticationPrincipal UserDetails user,
                                @PathVariable UUID meetingId) {
        return toTokenOut(join.join(currentUserId(user), meetingId));
    }

    @PostMapping("/{meetingId}/finish")
    @PreAuthorize("hasRole('PROVIDER')")
    public MeetingOut finish(@AuthenticationPrincipal UserDetails user,
                             @PathVariable UUID meetingId) {
        return toOut(manage.finishMeeting(currentUserId(user), meetingId));
    }

    @PostMapping("/{meetingId}/cancel")
    @PreAuthorize("hasRole('PROVIDER')")
    public MeetingOut cancel(@AuthenticationPrincipal UserDetails user,
                             @PathVariable UUID meetingId,
                             @Valid @RequestBody CancelMeetingRequest request) {
        return toOut(manage.cancelMeeting(currentUserId(user), meetingId, request.reason()));
    }

    /** Regla 4: extensión única de 20 minutos, sin renovación. */
    @PostMapping("/{meetingId}/extend")
    @PreAuthorize("hasRole('PROVIDER')")
    public MeetingOut extend(@AuthenticationPrincipal UserDetails user,
                             @PathVariable UUID meetingId) {
        return toOut(manage.extendMeeting(currentUserId(user), meetingId));
    }

    @GetMapping("/{meetingId}")
    public MeetingOut find(@AuthenticationPrincipal UserDetails user,
                           @PathVariable UUID meetingId) {
        Meeting meeting = retrieve.findById(meetingId)
            .orElseThrow(() -> new BusinessRuleException("Reunión no encontrada"));
        retrieve.findHistory(currentUserId(user), meetingId); // valida visibilidad (proveedor o inscrito)
        return toOut(meeting);
    }

    @GetMapping("/service/{serviceId}")
    public List<MeetingOut> byService(@AuthenticationPrincipal UserDetails user,
                                      @PathVariable UUID serviceId) {
        return retrieve.findByService(currentUserId(user), serviceId).stream()
            .map(this::toOut).toList();
    }

    @GetMapping("/{meetingId}/participants")
    public List<MeetingParticipantOut> participants(@AuthenticationPrincipal UserDetails user,
                                                    @PathVariable UUID meetingId) {
        return retrieve.findParticipants(currentUserId(user), meetingId).stream()
            .map(p -> new MeetingParticipantOut(p.id(), p.userId(), p.role(), p.status(),
                p.joinedAt(), p.leftAt(), p.denialReason()))
            .toList();
    }

    @GetMapping("/{meetingId}/history")
    public List<MeetingHistoryOut> history(@AuthenticationPrincipal UserDetails user,
                                           @PathVariable UUID meetingId) {
        return retrieve.findHistory(currentUserId(user), meetingId).stream()
            .map(h -> new MeetingHistoryOut(h.id(), h.userId(), h.eventType(), h.result(),
                h.reason(), h.metadata(), h.createdAt()))
            .toList();
    }

    private UUID currentUserId(UserDetails user) {
        return users.findByEmail(user.getUsername())
            .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado")).id();
    }

    private MeetingTokenOut toTokenOut(MeetingAccess access) {
        return new MeetingTokenOut(access.meeting().id(), access.livekitUrl(), access.token(),
            access.role(), access.tokenExpiresAt());
    }

    private MeetingOut toOut(Meeting m) {
        return new MeetingOut(m.id(), m.serviceId(), m.meetingDate(), m.startTime(),
            m.durationMinutes(), m.status(), m.scheduledStart(), m.actualStart(), m.actualEnd(),
            m.maxEnd(), m.extensionUsed(), m.cancellationReason(), m.cancelledBy(), m.createdAt());
    }
}
