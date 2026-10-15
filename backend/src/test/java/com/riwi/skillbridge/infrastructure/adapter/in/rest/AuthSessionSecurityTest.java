package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.ChangeStatusUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.DeleteUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.RetrieveUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.UpdateUserAccountUseCase;
import com.riwi.skillbridge.domain.enums.Gender;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.config.SecurityConfiguration;
import com.riwi.skillbridge.infrastructure.security.DatabaseUserDetailsService;
import com.riwi.skillbridge.infrastructure.security.JwtAuthenticationFilter;
import com.riwi.skillbridge.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserAccountController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, JwtService.class})
class AuthSessionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthUseCase authUseCase;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    @MockitoBean
    private RetrieveUserAccountUseCase retrieveUseCase;

    @MockitoBean
    private UpdateUserAccountUseCase updateUseCase;

    @MockitoBean
    private ChangeStatusUserAccountUseCase changeStatusUseCase;

    @MockitoBean
    private DeleteUserAccountUseCase deleteUseCase;

    @Test
    @DisplayName("Inicio de sesión exitoso emite cookie HttpOnly, SameSite, Path=/api y no expone el token en JSON")
    void login_issuesHttpOnlyCookie_andDoesNotExposeTokenInJson() throws Exception {
        when(authUseCase.login("user@test.com", "Password123!")).thenReturn("test.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.message").value("Inicio de sesión exitoso"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist());
    }

    @Test
    @DisplayName("Registro exitoso emite cookie HttpOnly y no expone el token en JSON")
    void register_issuesHttpOnlyCookie_andDoesNotExposeTokenInJson() throws Exception {
        when(authUseCase.register(anyString(), anyString(), anyString())).thenReturn("test.jwt.token");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carlos Gomez",
                                  "email": "carlos@test.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")))
                .andExpect(jsonPath("$.message").value("Usuario registrado exitosamente"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Cerrar sesión invalida la cookie con Max-Age=0")
    void logout_invalidatesCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("access_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));
    }

    @Test
    @DisplayName("Acceso autenticado con cookie de sesión a /api/users/me es exitoso")
    void authenticatedEndpoint_accessibleWithSessionCookie() throws Exception {
        String token = jwtService.generate("carlos@test.com", "CUSTOMER");

        when(userDetailsService.loadUserByUsername("carlos@test.com"))
                .thenReturn(new User("carlos@test.com", "hash", Collections.emptyList()));

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                "Carlos Gomez",
                "carlos@test.com",
                Role.CUSTOMER,
                null,
                "hash",
                Gender.MALE,
                LocalDate.of(1995, 5, 20),
                Status.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(retrieveUseCase.getUserAccountByEmail("carlos@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Gomez"))
                .andExpect(jsonPath("$.email").value("carlos@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("Solicitud autenticada no destruye la cookie XSRF-TOKEN en solicitudes subsiguientes")
    void authenticatedEndpoint_preservesXsrfToken() throws Exception {
        String token = jwtService.generate("carlos@test.com", "CUSTOMER");

        when(userDetailsService.loadUserByUsername("carlos@test.com"))
                .thenReturn(new User("carlos@test.com", "hash", Collections.emptyList()));

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                "Carlos Gomez",
                "carlos@test.com",
                Role.CUSTOMER,
                null,
                "hash",
                Gender.MALE,
                LocalDate.of(1995, 5, 20),
                Status.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(retrieveUseCase.getUserAccountByEmail("carlos@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", "test-token-123")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("XSRF-TOKEN=;"))));
    }

    @Test
    @DisplayName("Acceso a /api/users/me sin cookie de sesión es rechazado con 401 Unauthorized")
    void authenticatedEndpoint_rejectedWithoutCookie() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Solicitud que muta datos (POST) sin token CSRF es rechazada con 403 Forbidden")
    void mutatingRequest_withoutCsrf_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Solicitud que muta datos (POST) con token CSRF valido es aceptada")
    void mutatingRequest_withCsrf_isAccepted() throws Exception {
        when(authUseCase.login("user@test.com", "Password123!")).thenReturn("test.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@test.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk());
    }
}

