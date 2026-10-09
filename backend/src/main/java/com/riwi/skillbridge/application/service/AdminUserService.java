package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService implements AdminManageUsersUseCase {
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    public AdminUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public List<UserAccount> listAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public UserAccount getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado con ID: " + id));
    }

    @Override
    public UserAccount createUser(String name, String email, String password, Role role) {
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("El correo electrónico es requerido");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("El correo electrónico ya se encuentra registrado");
        }
        String passwordToHash = (password != null && !password.isBlank()) ? password : "TemporaryPass123*";
        String encodedPassword = passwordHasher.encode(passwordToHash);
        Role userRole = role != null ? role : Role.CUSTOMER;

        UserAccount newAccount = new UserAccount(UUID.randomUUID(), name, email.trim().toLowerCase(), encodedPassword, userRole);
        return userRepository.save(newAccount);
    }

    @Override
    public UserAccount updateUser(UUID id, String name, String email, Role role) {
        UserAccount current = getUserById(id);

        String newEmail = current.email();
        if (email != null && !email.isBlank()) {
            String trimmedEmail = email.trim().toLowerCase();
            if (!trimmedEmail.equalsIgnoreCase(current.email())) {
                if (userRepository.existsByEmail(trimmedEmail)) {
                    throw new BusinessRuleException("El correo electrónico ya se encuentra en uso");
                }
                newEmail = trimmedEmail;
            }
        }

        String newName = (name != null && !name.isBlank()) ? name : current.name();
        Role newRole = role != null ? role : current.role();

        UserAccount updated = new UserAccount(current.id(), newName, newEmail, current.passwordHash(), newRole);
        return userRepository.save(updated);
    }

    @Override
    public void deleteUser(UUID id) {
        getUserById(id); // Verifica existencia
        userRepository.deleteById(id);
    }
}

