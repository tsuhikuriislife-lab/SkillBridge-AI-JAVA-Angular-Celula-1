package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.model.CurrentUser;
import com.riwi.skillbridge.application.port.in.GetCurrentUserUseCase;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService implements GetCurrentUserUseCase {
    private final UserRepositoryPort users;

    public CurrentUserService(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public CurrentUser getByEmail(String email) {
        return users.findByEmail(email)
                .map(u -> new CurrentUser(u.name(), u.email(), u.role()))
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));
    }
}