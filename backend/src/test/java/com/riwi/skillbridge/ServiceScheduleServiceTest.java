package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceSchedulePort;
import com.riwi.skillbridge.application.service.ServiceScheduleService;
import com.riwi.skillbridge.domain.enums.Frequency;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.ServiceSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceScheduleServiceTest {

    @Mock ServiceSchedulePort schedulePort;
    @Mock OfferingPort offeringPort;
    @InjectMocks ServiceScheduleService service;

    private final UUID serviceId = UUID.randomUUID();
    private final LocalDate validStart = LocalDate.now().plusDays(7);

    private ServiceSchedule schedule(int sessions, int duration, Frequency freq, LocalDate start) {
        return new ServiceSchedule(null, serviceId, start.getDayOfWeek(), duration, freq, sessions, start, java.time.LocalTime.of(10, 0), java.time.LocalTime.of(11, 0));
    }

    private Offering service() {
        return new Offering(serviceId, "Curso", UUID.randomUUID(), BigDecimal.TEN,
                null, null, null, null, 20, "CODE-1", ServiceStatus.DRAFT, UUID.randomUUID(), null, null);
    }

    @Test
    void calculateEndDate_weekly() {
        var s = schedule(4, 60, Frequency.WEEKLY, validStart);
        assertEquals(validStart.plusWeeks(3), ServiceScheduleService.calculateEndDate(s));
    }

    @Test
    void calculateEndDate_biweekly() {
        var s = schedule(4, 60, Frequency.BIWEEKLY, validStart);
        assertEquals(validStart.plusWeeks(6), ServiceScheduleService.calculateEndDate(s));
    }

    @Test
    void calculateEndDate_monthly() {
        var s = schedule(3, 60, Frequency.MONTHLY, validStart);
        assertEquals(validStart.plusMonths(2), ServiceScheduleService.calculateEndDate(s));
    }

    @Test
    void create_rejectsStartWithin48h() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(service()));
        assertThrows(BusinessRuleException.class,
                () -> service.createSchedule(schedule(4, 60, Frequency.WEEKLY, LocalDate.now().plusDays(1))));
    }

    @Test
    void create_rejectsZeroSessions() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(service()));
        assertThrows(BusinessRuleException.class,
                () -> service.createSchedule(schedule(0, 60, Frequency.WEEKLY, validStart)));
    }

    @Test
    void create_rejectsZeroDuration() {
        when(offeringPort.findById(serviceId)).thenReturn(Optional.of(service()));
        assertThrows(BusinessRuleException.class,
                () -> service.createSchedule(schedule(4, 0, Frequency.WEEKLY, validStart)));
    }
}
