package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.useraccount.ChangeStatusUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.DeleteUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.RetrieveUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.UpdateUserAccountUseCase;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.Gender;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserAccountService implements RetrieveUserAccountUseCase, UpdateUserAccountUseCase,
    ChangeStatusUserAccountUseCase, DeleteUserAccountUseCase {

    private final UserAccountPort userAccountPort;

    public UserAccountService(UserAccountPort userAccountPort) {
        this.userAccountPort = userAccountPort;
    }

    @Override
    public Optional<UserAccount> getUserAccountById(UUID id) {
        return userAccountPort.findById(id);
    }

    @Override
    public PageResult<UserAccount> getAllUserAccounts(int page, int size) {
        return userAccountPort.findAll(page, size);
    }

    @Override
    public List<UserAccount> getAllUserAccounts() {
        return userAccountPort.findAll();
    }

    @Override
    public Optional<UserAccount> updateUserAccount(UUID id, String name, String image,
                                                   Gender gender, LocalDate birthDate) {
        // 🛡️ REGLA 1: name no puede quedar en blanco
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("El nombre no puede estar en blanco");
        }
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        return Optional.of(userAccountPort.save(new UserAccount(
            existing.id(),
            name.trim(),
            existing.email(),
            existing.role(),
            image,
            existing.password(),
            gender,
            birthDate,
            existing.status(),
            existing.createdAt(),
            OffsetDateTime.now()
        )));
    }

    @Override
    public Optional<UserAccount> changeStatus(UUID id, boolean active) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        return Optional.of(userAccountPort.save(new UserAccount(
            existing.id(),
            existing.name(),
            existing.email(),
            existing.role(),
            existing.image(),
            existing.password(),
            existing.gender(),
            existing.birthDate(),
            active ? Status.ACTIVE : Status.INACTIVE,
            existing.createdAt(),
            OffsetDateTime.now()
        )));
    }

    @Override
    public boolean deleteUserAccount(UUID id) {
        // 🛡️ REGLA 2: debe existir
        UserAccount existing = userAccountPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        // 🛡️ REGLA 4 (bonus): no eliminar administradores
        if (existing.role() == Role.ADMIN) {
            throw new BusinessRuleException("No se puede eliminar un usuario administrador");
        }

        userAccountPort.deleteById(id);
        return true;
    }

    @Override
    public List<UserAccount> searchByName(String name) {
        // 🛡️ REGLA 3: término de búsqueda obligatorio
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("El término de búsqueda por nombre no puede estar en blanco");
        }
        return userAccountPort.searchByName(name.trim());
    }

    @Override
    public List<UserAccount> searchByEmail(String email) {
        // 🛡️ REGLA 3
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("El término de búsqueda por email no puede estar en blanco");
        }
        return userAccountPort.searchByEmail(email.trim());
    }

    @Override
    public List<UserAccount> getAllSortedByName() {
        return userAccountPort.findAllSortedByName();
    }

    @Override
    public List<UserAccount> getUsersByService(UUID serviceId) {
        if (serviceId == null) {
            throw new BusinessRuleException("El id del servicio es obligatorio");
        }
        return userAccountPort.findByServiceId(serviceId);
    }
}
