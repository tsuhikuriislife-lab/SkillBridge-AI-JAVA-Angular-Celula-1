package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

@Service
public class AdminUserService implements AdminManageUsersUseCase {
    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final AdminActivityUseCase adminActivity;

    public AdminUserService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
                            AdminActivityUseCase adminActivity) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.adminActivity = adminActivity;
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
    public UserAccount createUser(String name, String email, String password, Role role, String actorEmail) {
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("El correo electrónico es requerido");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("El correo electrónico ya se encuentra registrado");
        }
        String passwordToHash = (password != null && !password.isBlank()) ? password : "TemporaryPass123*";
        String encodedPassword = passwordHasher.encode(passwordToHash);
        Role userRole = role != null ? role : Role.CUSTOMER;
        ensureAdminRoleAvailable(userRole, null);

        OffsetDateTime now = OffsetDateTime.now();
        UserAccount newAccount = new UserAccount(
            UUID.randomUUID(), name, email.trim().toLowerCase(), userRole, null,
            encodedPassword, null, null, Status.ACTIVE, now, now);
        UserAccount saved = userRepository.save(newAccount);
        adminActivity.record(actorEmail, "USER_CREATED", "USER", saved.id(),
            "Se creó el usuario " + saved.name() + " (" + saved.email() + ") con rol " + saved.role() + ".");
        return saved;
    }

    @Override
    public UserAccount updateUser(UUID id, String name, String email, Role role, String actorEmail) {
        UserAccount current = getUserById(id);
        if (current.role() == Role.ADMIN && role != null && role != Role.ADMIN) {
            throw new BusinessRuleException("No se puede cambiar el rol del único administrador");
        }

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
        ensureAdminRoleAvailable(newRole, current.id());

        UserAccount updated = new UserAccount(
            current.id(), newName, newEmail, newRole, current.image(), current.password(),
            current.gender(), current.birthDate(), current.status(), current.createdAt(), OffsetDateTime.now());
        UserAccount saved = userRepository.save(updated);
        String action = current.role() == saved.role() ? "USER_UPDATED" : "USER_ROLE_CHANGED";
        adminActivity.record(actorEmail, action, "USER", saved.id(),
            "Se actualizó " + saved.email() + "; rol: " + current.role() + " -> " + saved.role() + ".");
        return saved;
    }

    @Override
    public void deleteUser(UUID id, String actorEmail) {
        UserAccount user = getUserById(id);
        if (user.role() == Role.ADMIN) {
            throw new BusinessRuleException("No se puede eliminar al único administrador");
        }
        userRepository.deleteById(id);
        adminActivity.record(actorEmail, "USER_DELETED", "USER", user.id(),
            "Se eliminó el usuario " + user.name() + " (" + user.email() + ").");
    }

    private void ensureAdminRoleAvailable(Role role, UUID currentUserId) {
        if (role == Role.ADMIN && userRepository.countByRole(Role.ADMIN) > 0) {
            boolean currentUserIsAdmin = currentUserId != null
                    && userRepository.findById(currentUserId).map(user -> user.role() == Role.ADMIN).orElse(false);
            if (!currentUserIsAdmin) {
                throw new BusinessRuleException("Ya existe un administrador registrado");
            }
        }
    }
}

