package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserAccountPort users;
    @Mock PasswordHasherPort passwords;
    @Mock TokenPort tokens;
    @Mock UserEventEmitter events;
    @InjectMocks AuthService authService;

    private static UserAccount user(Status status) {
        return new UserAccount(UUID.randomUUID(), "Ana", "ana@x.com", Role.CUSTOMER, null, "hash",
                null, null, status, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    void login_activeUser_returnsToken() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.ACTIVE)));
        when(passwords.matches("Clave123*", "hash")).thenReturn(true);
        when(tokens.generate("ana@x.com", "CUSTOMER")).thenReturn("jwt");

        assertEquals("jwt", authService.login(" Ana@X.com ", "Clave123*"));
    }

    @Test
    void login_inactiveUser_isRejected_andNoTokenIsIssued() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.INACTIVE)));
        when(passwords.matches("Clave123*", "hash")).thenReturn(true);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("ana@x.com", "Clave123*"));
        assertTrue(ex.getMessage().contains("desactivada"));
        verify(tokens, never()).generate(anyString(), anyString());
    }

    @Test
    void login_inactiveUser_withWrongPassword_doesNotRevealStatus() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(user(Status.INACTIVE)));
        when(passwords.matches("mala", "hash")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login("ana@x.com", "mala"));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void register_publishesRegisteredEvent() {
        when(users.existsByEmail("nuevo@x.com")).thenReturn(false);
        when(passwords.encode("Clave123*")).thenReturn("hash");
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokens.generate("nuevo@x.com", "CUSTOMER")).thenReturn("jwt");

        authService.register("Nuevo", "nuevo@x.com", "Clave123*");

        verify(events).registered(any(UserAccount.class));
    }
}
