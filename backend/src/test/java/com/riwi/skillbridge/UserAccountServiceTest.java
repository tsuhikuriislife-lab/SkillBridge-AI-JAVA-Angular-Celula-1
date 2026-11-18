package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.UserAccountService;
import com.riwi.skillbridge.domain.enums.*;
import com.riwi.skillbridge.domain.exception.AccountAccessDeniedException;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock UserAccountPort userAccountPort;
    @Mock com.riwi.skillbridge.application.service.UserEventEmitter events;
    @InjectMocks UserAccountService service;

    private final UUID id = UUID.randomUUID();

    private UserAccount existing(Role role) {
        return new UserAccount(id, "Ana", "ana@x.com", role, null, "hash",
                Gender.FEMALE, null, Status.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void update_rejectsBlankName() {
        assertThrows(BusinessRuleException.class,
                () -> service.updateUserAccount(id, "  ", null, null, null));
    }

    @Test
    void update_throwsWhenUserNotFound() {
        when(userAccountPort.findById(id)).thenReturn(Optional.empty());
        assertThrows(DomainNotFoundException.class,
                () -> service.updateUserAccount(id, "Ana", null, null, null));
    }

    @Test
    void update_keepsProtectedFields() {
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        when(userAccountPort.save(any())).thenAnswer(i -> i.getArgument(0));

        var updated = service.updateUserAccount(id, "Ana María", "img.png", Gender.FEMALE, null);

        assertEquals("ana@x.com", updated.get().email());
        assertEquals("hash", updated.get().password());
        assertEquals(Role.CUSTOMER, updated.get().role());
        assertEquals("img.png", updated.get().image());
    }

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

    @Test
    void delete_rejectsAdmin() {
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.ADMIN)));
        assertThrows(BusinessRuleException.class, () -> service.deleteUserAccount(id, "ana@x.com"));
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(userAccountPort.findById(id)).thenReturn(Optional.empty());
        assertThrows(DomainNotFoundException.class, () -> service.deleteUserAccount(id, "ana@x.com"));
    }

    @Test
    void getByEmail_returnsEmptyWhenBlank() {
        assertTrue(service.getUserAccountByEmail("").isEmpty());
        assertTrue(service.getUserAccountByEmail(null).isEmpty());
    }

    @Test
    void getByEmail_findsUserNormalized() {
        when(userAccountPort.findByEmail("ana@x.com")).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        var result = service.getUserAccountByEmail("  ANA@X.COM  ");
        assertTrue(result.isPresent());
        assertEquals("ana@x.com", result.get().email());
    }
}
