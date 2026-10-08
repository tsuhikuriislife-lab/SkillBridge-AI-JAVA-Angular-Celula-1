package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.CurrentUser;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepositoryPort users;

    @InjectMocks
    private CurrentUserService service;

    @Test
    void getByEmail_returnsNameEmailAndRoleFromDatabase() {
        UserAccount account = new UserAccount(UUID.randomUUID(), "Guillermo", "guillermo@correo.com",
                "hash-secreto", Role.PROVIDER);
        when(users.findByEmail("guillermo@correo.com")).thenReturn(Optional.of(account));

        CurrentUser result = service.getByEmail("guillermo@correo.com");

        assertEquals("Guillermo", result.name());
        assertEquals("guillermo@correo.com", result.email());
        assertEquals(Role.PROVIDER, result.role());
    }

    @Test
    void getByEmail_unknownUser_throwsNotFound() {
        when(users.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.getByEmail("nadie@correo.com"));
    }
}