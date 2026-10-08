package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.TokenPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.InvalidCredentialsException;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService implements AuthUseCase {

    private final UserAccountPort users;
    private final PasswordHasherPort passwords;
    private final TokenPort tokens;

    public AuthService(UserAccountPort users, PasswordHasherPort passwords, TokenPort tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Override
    public String register(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleException("El correo ya está registrado");
        }
        // Registro: solo name, email, password. Lo demás null hasta que el usuario lo complete.
        UserAccount saved = users.save(new UserAccount(
            UUID.randomUUID(),
            name.trim(),
            normalizedEmail,
            Role.CUSTOMER,                  // rol por defecto
            null,                           // image
            passwords.encode(rawPassword),  // password (ya hasheado)
            null,                           // gender
            null,                           // birthDate
            Status.ACTIVE,
            OffsetDateTime.now(),
            OffsetDateTime.now()));
        return tokens.generate(saved.email(), saved.role().name());
    }

    @Override
    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.trim().toLowerCase())
            .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
        if (!passwords.matches(rawPassword, user.password())) {   // ✅ accessor del record
            throw new InvalidCredentialsException("Credenciales inválidas");
        }
        return tokens.generate(user.email(), user.role().name());
    }
}
