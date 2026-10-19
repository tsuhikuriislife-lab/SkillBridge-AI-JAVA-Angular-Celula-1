package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.application.port.out.TokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

@Service
public class JwtService implements TokenPort {
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "change-this-to-a-long-random-secret-at-least-32-characters",
            "12345678901234567890123456789012",
            "change-this-secret-key-please-12345",
            "default-secret-key-change-this-in-production",
            "your-jwt-secret-key-must-be-at-least-32-chars",
            "secret-key-at-least-32-characters-long",
            "supersecretkey12345678901234567890"
    );

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes) {
        validateSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.trim().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must be provided via environment variable and cannot be blank");
        }
        String trimmed = secret.trim();
        if (trimmed.length() < 32) {
            throw new IllegalArgumentException("JWT secret must have at least 32 characters");
        }
        if (KNOWN_WEAK_SECRETS.contains(trimmed.toLowerCase())) {
            throw new IllegalArgumentException("JWT secret matches a known insecure or default value");
        }
        long uniqueChars = trimmed.chars().distinct().count();
        if (uniqueChars < 8) {
            throw new IllegalArgumentException("JWT secret is too weak (insufficient entropy)");
        }
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public String generate(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) { return claims(token).getSubject(); }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = claims(token);
        return claims.getSubject().equalsIgnoreCase(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
