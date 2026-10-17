package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AuthResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.LoginRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.RegisterRequest;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthUseCase auth;
    private final JwtService jwtService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookiePath;

    public AuthController(AuthUseCase auth,
                          JwtService jwtService,
                          @Value("${app.security.cookie.name:access_token}") String cookieName,
                          @Value("${app.security.cookie.secure:false}") boolean cookieSecure,
                          @Value("${app.security.cookie.same-site:Lax}") String cookieSameSite,
                          @Value("${app.security.cookie.path:/api}") String cookiePath) {
        this.auth = auth;
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookiePath = cookiePath;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        String token = auth.register(request.name(), request.email(), request.password());
        ResponseCookie cookie = createCookie(token, jwtService.getExpirationMinutes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse("Usuario registrado exitosamente"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = auth.login(request.email(), request.password());
        ResponseCookie cookie = createCookie(token, jwtService.getExpirationMinutes());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse("Inicio de sesión exitoso"));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthResponse> logout() {
        ResponseCookie cleanCookie = clearCookie();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(new AuthResponse("Sesión cerrada exitosamente"));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie createCookie(String token, long expirationMinutes) {
        return ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(Duration.ofMinutes(expirationMinutes))
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
    }
}
