package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AdminActivityRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminActivityServiceTest {
    private final AdminActivityRepositoryPort activities = mock(AdminActivityRepositoryPort.class);
    private final UserAccountPort users = mock(UserAccountPort.class);
    private final AdminActivityService service = new AdminActivityService(activities, users);

    @Test
    void record_shouldPersistActivityForAuthenticatedAdmin() {
        UUID adminId = UUID.randomUUID();
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user(adminId, Role.ADMIN)));

        service.record("admin@example.com", "USER_ROLE_CHANGED", "USER", UUID.randomUUID(), "Role changed");

        verify(activities).save(any(AdminActivity.class));
    }

    @Test
    void record_shouldRejectNonAdminAndNotPersist() {
        when(users.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), Role.CUSTOMER)));

        assertThrows(BusinessRuleException.class, () -> service.record(
                "customer@example.com", "USER_ROLE_CHANGED", "USER", UUID.randomUUID(), "Role changed"));

        verifyNoInteractions(activities);
    }

    @Test
    void listMine_shouldQueryOnlyAuthenticatedAdmin() {
        UUID adminId = UUID.randomUUID();
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user(adminId, Role.ADMIN)));
        PageResult<AdminActivity> expected = new PageResult<>(List.of(), 0, 0, 0);
        when(activities.findPageByActorId(adminId, 0, 20)).thenReturn(expected);

        assertEquals(expected, service.listMine("admin@example.com", 0, 20));
        verify(activities).findPageByActorId(adminId, 0, 20);
    }

    private static UserAccount user(UUID id, Role role) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UserAccount(id, "User", "user@example.com", role, null, "hash",
                null, null, Status.ACTIVE, now, now);
    }
}
