package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.CurrentUser;
import com.riwi.skillbridge.application.port.in.GetCurrentUserUseCase;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String EMAIL = "guillermo@correo.com";

    @Mock
    private GetCurrentUserUseCase useCase;

    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken(EMAIL, null);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void me_returnsCurrentUserWithRole() throws Exception {
        when(useCase.getByEmail(EMAIL)).thenReturn(new CurrentUser("Guillermo", EMAIL, Role.PROVIDER));

        mvc.perform(get("/api/users/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Guillermo"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_unknownUser_returns404() throws Exception {
        when(useCase.getByEmail(EMAIL)).thenThrow(new DomainNotFoundException("Usuario no encontrado"));

        mvc.perform(get("/api/users/me").principal(principal))
                .andExpect(status().isNotFound());
    }
}