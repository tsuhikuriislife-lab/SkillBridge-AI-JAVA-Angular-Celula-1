package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.LiveKitPort;
import com.riwi.skillbridge.application.port.out.MeetingHistoryPort;
import com.riwi.skillbridge.application.port.out.MeetingParticipantPort;
import com.riwi.skillbridge.application.port.out.MeetingPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.enums.Frequency;
import com.riwi.skillbridge.domain.enums.MeetingRole;
import com.riwi.skillbridge.domain.enums.MeetingStatus;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Meeting;
import com.riwi.skillbridge.domain.model.MeetingParticipant;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceEnrollment;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MeetingServiceTest {

    private final MeetingPort meetingPort = mock(MeetingPort.class);
    private final MeetingParticipantPort participantPort = mock(MeetingParticipantPort.class);
    private final MeetingHistoryPort historyPort = mock(MeetingHistoryPort.class);
    private final LiveKitPort liveKitPort = mock(LiveKitPort.class);
    private final OfferingPort offeringPort = mock(OfferingPort.class);
    private final ServiceSchedulePort schedulePort = mock(ServiceSchedulePort.class);
    private final ServiceEnrollmentPort enrollmentPort = mock(ServiceEnrollmentPort.class);

    // 2026-10-20 es martes; sesión programada a las 15:00 UTC
    private final Instant nowInstant = Instant.parse("2026-10-20T15:05:00Z");
    private final Clock clock = Clock.fixed(nowInstant, ZoneOffset.UTC);
    private final UUID providerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private MeetingService service() {
        return service(clock);
    }

    private MeetingService service(Clock testClock) {
        MeetingService s = new MeetingService(meetingPort, participantPort, historyPort, liveKitPort,
            offeringPort, schedulePort, enrollmentPort, testClock);
        when(liveKitPort.serverUrl()).thenReturn("wss://livekit.example.com");
        when(liveKitPort.createAccessToken(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn("lk-token");
        when(meetingPort.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return s;
    }

    private Offering offering() {
        return new Offering(serviceId, "Curso de prueba", UUID.randomUUID(), BigDecimal.TEN, null,
            null, null, null, 20, "CODE-1", ServiceStatus.ACTIVE, providerId,
            OffsetDateTime.now(), OffsetDateTime.now());
    }

    private ServiceSchedule schedule() {
        return new ServiceSchedule(UUID.randomUUID(), serviceId, java.time.DayOfWeek.TUESDAY,
            120, Frequency.WEEKLY, 4, LocalDate.of(2026, 10, 20),
            LocalTime.of(15, 0), LocalTime.of(17, 0));
    }

    private Meeting programmedMeeting(UUID scheduleId) {
        UUID meetingId = UUID.randomUUID();
        return new Meeting(meetingId, serviceId, scheduleId, LocalDate.of(2026, 10, 20),
            LocalTime.of(15, 0), 120, MeetingStatus.PROGRAMADA, meetingId.toString(), false,
            OffsetDateTime.ofInstant(nowInstant, ZoneOffset.UTC).minusMinutes(5),
            null, null, null, 20, false, null, null,
            OffsetDateTime.now(), OffsetDateTime.now());
    }

    // ------------------------------------------------------------- Regla 1

    @Test
    void startMeeting_rejectsNonOwnerProvider() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        MeetingService s = service();
        assertThrows(BusinessRuleException.class,
            () -> s.startMeeting(UUID.randomUUID(), serviceId));
        verify(liveKitPort, never()).createRoom(anyString());
    }

    @Test
    void startMeeting_outsideWindow_marksNoRealizada() {
        Instant late = Instant.parse("2026-10-20T16:00:00Z"); // 60 min después: fuera de la ventana
        ServiceSchedule schedule = schedule();
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        when(schedulePort.findByServiceId(serviceId)).thenReturn(List.of(schedule));
        Meeting existing = programmedMeeting(schedule.id());
        when(meetingPort.findByServiceIdAndScheduleIdAndMeetingDate(eq(serviceId), eq(schedule.id()), any()))
            .thenReturn(Optional.of(existing));

        MeetingService s = service(Clock.fixed(late, ZoneOffset.UTC));
        assertThrows(BusinessRuleException.class, () -> s.startMeeting(providerId, serviceId));

        ArgumentCaptor<Meeting> captor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingPort).save(captor.capture());
        assertEquals(MeetingStatus.NO_REALIZADA, captor.getValue().status());
        verify(liveKitPort, never()).createRoom(anyString());
    }

    @Test
    void startMeeting_beforeScheduledTime_isRejected() {
        Instant early = Instant.parse("2026-10-20T14:59:00Z");
        MeetingService s = new MeetingService(meetingPort, participantPort, historyPort, liveKitPort,
            offeringPort, schedulePort, enrollmentPort,
            Clock.fixed(early, ZoneOffset.UTC));
        ServiceSchedule schedule = schedule();
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        when(schedulePort.findByServiceId(serviceId)).thenReturn(List.of(schedule));
        when(meetingPort.findByServiceIdAndScheduleIdAndMeetingDate(eq(serviceId), eq(schedule.id()), any()))
            .thenReturn(Optional.of(programmedMeeting(schedule.id())));

        assertThrows(BusinessRuleException.class, () -> s.startMeeting(providerId, serviceId));
    }

    // ------------------------------------------------------------- Regla 2

    @Test
    void join_withoutActiveEnrollment_isDenied() {
        MeetingService s = service();
        Meeting meeting = inProgressMeeting();
        when(meetingPort.findById(meeting.id())).thenReturn(Optional.of(meeting));
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        when(enrollmentPort.findByUserIdAndServiceId(customerId, serviceId)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> s.join(customerId, meeting.id()));
        verify(liveKitPort, never()).createAccessToken(anyString(), anyString(), anyString(), any(), any());
        verify(participantPort).save(any(MeetingParticipant.class));
        verify(historyPort).save(any());
    }

    @Test
    void join_withActiveEnrollment_getsParticipantTokenWithoutPublish() {
        MeetingService s = service();
        Meeting meeting = inProgressMeeting();
        when(meetingPort.findById(meeting.id())).thenReturn(Optional.of(meeting));
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        when(enrollmentPort.findByUserIdAndServiceId(customerId, serviceId))
            .thenReturn(Optional.of(new ServiceEnrollment(customerId, serviceId,
                EnrollmentStatus.ACTIVE, 3, LocalDate.of(2026, 10, 20), null)));
        when(participantPort.findByMeetingIdAndUserId(meeting.id(), customerId))
            .thenReturn(Optional.empty());

        var access = s.join(customerId, meeting.id());

        assertEquals(MeetingRole.PARTICIPANT, access.role());
        ArgumentCaptor<MeetingRole> roleCaptor = ArgumentCaptor.forClass(MeetingRole.class);
        verify(liveKitPort).createAccessToken(eq(meeting.livekitRoom()), eq(customerId.toString()),
            anyString(), roleCaptor.capture(), any());
        assertEquals(MeetingRole.PARTICIPANT, roleCaptor.getValue());
    }

    @Test
    void join_beforeStart_isDenied() {
        MeetingService s = service();
        Meeting meeting = programmedMeeting(UUID.randomUUID());
        when(meetingPort.findById(meeting.id())).thenReturn(Optional.of(meeting));
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        when(enrollmentPort.findByUserIdAndServiceId(customerId, serviceId))
            .thenReturn(Optional.of(new ServiceEnrollment(customerId, serviceId,
                EnrollmentStatus.ACTIVE, 3, LocalDate.of(2026, 10, 20), null)));

        assertThrows(BusinessRuleException.class, () -> s.join(customerId, meeting.id()));
    }

    // ------------------------------------------------------------- Regla 4

    @Test
    void extendMeeting_canOnlyBeUsedOnce() {
        Meeting inProgress = inProgressMeeting();
        Meeting extended = new Meeting(inProgress.id(), inProgress.serviceId(), inProgress.scheduleId(),
            inProgress.meetingDate(), inProgress.startTime(), inProgress.durationMinutes(),
            MeetingStatus.EN_CURSO, inProgress.livekitRoom(), true,
            inProgress.scheduledStart(), inProgress.actualStart(), null,
            inProgress.actualStart().plusMinutes(140), 20, true, null, null,
            inProgress.createdAt(), inProgress.updatedAt());
        when(meetingPort.findById(inProgress.id())).thenReturn(Optional.of(inProgress), Optional.of(extended));
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering()));
        MeetingService s = service();

        s.extendMeeting(providerId, inProgress.id());
        assertThrows(BusinessRuleException.class, () -> s.extendMeeting(providerId, inProgress.id()));
    }

    // ---------------------------------------------------------- utilidades

    private Meeting inProgressMeeting() {
        UUID meetingId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.ofInstant(nowInstant, ZoneOffset.UTC).minusMinutes(5);
        return new Meeting(meetingId, serviceId, UUID.randomUUID(), LocalDate.of(2026, 10, 20),
            LocalTime.of(15, 0), 120, MeetingStatus.EN_CURSO, meetingId.toString(), true,
            start.minusMinutes(5), start, null, start.plusMinutes(115), 20, false,
            null, null, OffsetDateTime.now(), OffsetDateTime.now());
    }
}
