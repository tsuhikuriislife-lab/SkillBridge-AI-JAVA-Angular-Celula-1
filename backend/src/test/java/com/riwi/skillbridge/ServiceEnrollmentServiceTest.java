package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.application.service.ServiceEnrollmentService;
import com.riwi.skillbridge.domain.enums.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceEnrollmentServiceTest {

    @Mock ServiceEnrollmentPort enrollmentPort;
    @Mock OfferingPort offeringPort;
    @Mock ServiceSchedulePort schedulePort;
    @Mock UserAccountPort userAccountPort;
    @Mock BookingHistoryPort historyPort;
    @InjectMocks ServiceEnrollmentService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    private Offering offering(int capacity, BigDecimal price) {
        return new Offering(serviceId, "Curso", UUID.randomUUID(), price, null, null, null,
                null, capacity, "C1", ServiceStatus.ACTIVE, providerId, null, null);
    }

    private ServiceSchedule schedule() {
        return new ServiceSchedule(UUID.randomUUID(), serviceId,
                LocalDate.now().plusDays(7).getDayOfWeek(), 60, Frequency.WEEKLY, 4,
                LocalDate.now().plusDays(7));
    }

    private void stubEnrollable(int capacity) {
        lenient().when(userAccountPort.findById(userId)).thenReturn(Optional.of(mock(UserAccount.class)));
        lenient().when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering(capacity, BigDecimal.TEN)));
        lenient().when(schedulePort.findByServiceId(serviceId)).thenReturn(List.of(schedule()));
        lenient().when(enrollmentPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(Optional.empty());
        lenient().when(enrollmentPort.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void enroll_ok_setsRemainingSessionsAndEndDate() {
        stubEnrollable(20);
        var e = service.enroll(new ServiceEnrollment(userId, serviceId, null, 0, null, null));
        assertEquals(EnrollmentStatus.ACTIVE, e.status());
        assertEquals(4, e.remainingSessions());
        assertEquals(LocalDate.now().plusDays(7).plusWeeks(3), e.endDate());
        verify(historyPort).save(argThat(h -> h.status() == EnrollmentStatus.ACTIVE));
    }

    @Test
    void enroll_rejectsWhenServiceNotActive() {
        when(userAccountPort.findById(userId)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(
                new Offering(serviceId, "S", UUID.randomUUID(), BigDecimal.ONE, null, null, null,
                        null, 5, "C", ServiceStatus.DRAFT, providerId, null, null)));
        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new ServiceEnrollment(userId, serviceId, null, 0, null, null)));
    }

    @Test
    void enroll_rejectsDuplicateActiveEnrollment() {
        stubEnrollable(20);
        when(enrollmentPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(Optional.of(
                new ServiceEnrollment(userId, serviceId, EnrollmentStatus.ACTIVE, 4,
                        LocalDate.now().plusDays(7), null)));
        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new ServiceEnrollment(userId, serviceId, null, 0, null, null)));
    }

    @Test
    void enroll_rejectsWhenFull() {
        stubEnrollable(1);
        when(enrollmentPort.countByServiceIdAndStatus(serviceId, EnrollmentStatus.ACTIVE)).thenReturn(1L);
        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new ServiceEnrollment(userId, serviceId, null, 0, null, null)));
    }

    @Test
    void withdraw_within24h_refunds() {
        var enrollment = new ServiceEnrollment(userId, serviceId, EnrollmentStatus.ACTIVE, 4,
                LocalDate.now().plusDays(7), null);
        when(enrollmentPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(Optional.of(enrollment));
        when(historyPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(List.of(
                new BookingHistory(UUID.randomUUID(), userId, serviceId, EnrollmentStatus.ACTIVE,
                        OffsetDateTime.now().minusHours(2), null)));
        when(enrollmentPort.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.withdraw(userId, serviceId);
        assertTrue(result.refund());
        assertEquals(EnrollmentStatus.CANCELLED, result.enrollment().status());
    }

    @Test
    void withdraw_after24h_noRefund() {
        var enrollment = new ServiceEnrollment(userId, serviceId, EnrollmentStatus.ACTIVE, 4,
                LocalDate.now().plusDays(7), null);
        when(enrollmentPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(Optional.of(enrollment));
        when(historyPort.findByUserIdAndServiceId(userId, serviceId)).thenReturn(List.of(
                new BookingHistory(UUID.randomUUID(), userId, serviceId, EnrollmentStatus.ACTIVE,
                        OffsetDateTime.now().minusDays(3), null)));
        when(enrollmentPort.save(any())).thenAnswer(i -> i.getArgument(0));

        assertFalse(service.withdraw(userId, serviceId).refund());
    }

    @Test
    void removeParticipant_rejectsPaidService() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering(20, BigDecimal.TEN)));
        assertThrows(BusinessRuleException.class,
                () -> service.removeParticipant(providerId, serviceId, userId));
    }

    @Test
    void removeParticipant_rejectsForeignService() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(offering(20, BigDecimal.ZERO)));
        assertThrows(BusinessRuleException.class,
                () -> service.removeParticipant(UUID.randomUUID(), serviceId, userId));
    }
}
