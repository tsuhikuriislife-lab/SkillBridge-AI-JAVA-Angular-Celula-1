package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.CurrentUser;
import com.riwi.skillbridge.application.port.in.GetCurrentUserUseCase;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final GetCurrentUserUseCase useCase;

    public UserController(GetCurrentUserUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        return useCase.getByEmail(authentication.getName());
    }
}