package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.UserAccountService;
import com.riwi.skillbridge.domain.enums.*;
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
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.CUSTOMER)));
        when(userAccountPort.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(Status.INACTIVE, service.changeStatus(id, false).get().status());
        assertEquals(Status.ACTIVE, service.changeStatus(id, true).get().status());
    }

    @Test
    void delete_rejectsAdmin() {
        when(userAccountPort.findById(id)).thenReturn(Optional.of(existing(Role.ADMIN)));
        assertThrows(BusinessRuleException.class, () -> service.deleteUserAccount(id));
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(userAccountPort.findById(id)).thenReturn(Optional.empty());
        assertThrows(DomainNotFoundException.class, () -> service.deleteUserAccount(id));
    }
}
