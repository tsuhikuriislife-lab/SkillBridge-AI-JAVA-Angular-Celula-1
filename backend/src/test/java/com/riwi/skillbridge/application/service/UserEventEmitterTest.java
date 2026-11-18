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
