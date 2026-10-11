package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AdminManageUsersUseCase;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AdminUserResponse;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreateAdminUserRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UpdateAdminUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminManageUsersUseCase manageUsersUseCase;

    public AdminUserController(AdminManageUsersUseCase manageUsersUseCase) {
        this.manageUsersUseCase = manageUsersUseCase;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> listAll() {
        List<AdminUserResponse> responses = manageUsersUseCase.listAllUsers().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getById(@PathVariable UUID id) {
        UserAccount user = manageUsersUseCase.getUserById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request,
                                                     Authentication authentication) {
        Role role = parseRole(request.role());
        UserAccount created = manageUsersUseCase.createUser(
                request.name(),
                request.email(),
                request.password(),
                role,
                authentication.getName()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserResponse> update(@PathVariable UUID id, @RequestBody UpdateAdminUserRequest request,
                                                     Authentication authentication) {
        Role role = request.role() != null ? parseRole(request.role()) : null;
        UserAccount updated = manageUsersUseCase.updateUser(
            id, request.name(), request.email(), role, authentication.getName());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        manageUsersUseCase.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private AdminUserResponse toResponse(UserAccount user) {
        return new AdminUserResponse(
                user.id(),
                user.name(),
                user.name(),
                user.email(),
                user.role().name(),
                true,
                "Activo"
        );
    }

    private Role parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return Role.CUSTOMER;
        }
        try {
            return Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Role.CUSTOMER;
        }
    }
}

