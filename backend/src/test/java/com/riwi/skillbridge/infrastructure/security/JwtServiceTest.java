package com.riwi.skillbridge.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String VALID_SECRET = "StrongSecretKeyForSkillBridge2026!@#";

    @Test
    @DisplayName("Falla al iniciar si JWT_SECRET es nulo")
    void constructor_failsWhenSecretIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService(null, 60));
        assertTrue(ex.getMessage().contains("JWT secret must be provided"));
    }

    @Test
    @DisplayName("Falla al iniciar si JWT_SECRET esta en blanco")
    void constructor_failsWhenSecretIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService("   ", 60));
        assertTrue(ex.getMessage().contains("JWT secret must be provided"));
    }

    @Test
    @DisplayName("Falla al iniciar si JWT_SECRET tiene menos de 32 caracteres")
    void constructor_failsWhenSecretIsTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService("short-secret-less-32-chars", 60));
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    @DisplayName("Falla al iniciar si JWT_SECRET coincide con un valor por defecto o conocido")
    void constructor_failsWhenSecretIsKnownWeakValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService("change-this-to-a-long-random-secret-at-least-32-characters", 60));
        assertTrue(ex.getMessage().contains("matches a known insecure"));

        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("12345678901234567890123456789012", 60));
    }

    @Test
    @DisplayName("Falla al iniciar si JWT_SECRET tiene baja entropia / caracteres repetitivos")
    void constructor_failsWhenSecretHasLowEntropy() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new JwtService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 60));
        assertTrue(ex.getMessage().contains("insufficient entropy"));
    }

    @Test
    @DisplayName("Inicializa exitosamente con un secreto seguro y genera token valido")
    void generateAndValidate_succeedsWithStrongSecret() {
        JwtService jwtService = new JwtService(VALID_SECRET, 60);
        assertEquals(60, jwtService.getExpirationMinutes());

        String token = jwtService.generate("user@skillbridge.com", "CUSTOMER");
        assertNotNull(token);
        assertEquals("user@skillbridge.com", jwtService.extractUsername(token));

        UserDetails userDetails = new User("user@skillbridge.com", "pass", Collections.emptyList());
        assertTrue(jwtService.isValid(token, userDetails));

        UserDetails differentUser = new User("other@skillbridge.com", "pass", Collections.emptyList());
        assertFalse(jwtService.isValid(token, differentUser));
    }
}

