package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private AdminActivityUseCase adminActivity;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, passwordHasher, adminActivity);
    }

    @Test
    void listAllUsers_shouldReturnList() {
        UserAccount user = user(UUID.randomUUID(), "Juan", "juan@test.com", "hash", Role.CUSTOMER);
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserAccount> result = adminUserService.listAllUsers();

        assertEquals(1, result.size());
        assertEquals("Juan", result.getFirst().name());
    }

    @Test
    void getUserById_whenExists_shouldReturnUser() {
        UUID id = UUID.randomUUID();
        UserAccount user = user(id, "Admin", "admin@test.com", "hash", Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserAccount result = adminUserService.getUserById(id);

        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void getUserById_whenNotFound_shouldThrow() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> adminUserService.getUserById(id));
    }

    @Test
    void createUser_whenEmailExists_shouldThrow() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
            adminUserService.createUser("Test", "test@test.com", "pass", Role.CUSTOMER, "admin@test.com"));
    }

    @Test
    void createUser_success() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordHasher.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = adminUserService.createUser(
            "New User", "new@test.com", "pass", Role.CUSTOMER, "admin@test.com");

        assertNotNull(created);
        assertEquals("New User", created.name());
        assertEquals("new@test.com", created.email());
        assertEquals("encoded", created.password());
        assertEquals(Role.CUSTOMER, created.role());
    }

        @Test
        void createUser_whenAdminAlreadyExists_shouldRejectSecondAdmin() {
        when(userRepository.existsByEmail("second-admin@test.com")).thenReturn(false);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () ->
            adminUserService.createUser("Second Admin", "second-admin@test.com", "pass", Role.ADMIN,
                "admin@test.com"));

        verify(userRepository, never()).save(any());
        }

        @Test
        void updateUser_whenAnotherAdminAlreadyExists_shouldRejectPromotion() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(
            user(id, "Customer", "customer@test.com", "hash", Role.CUSTOMER)));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () ->
            adminUserService.updateUser(id, null, null, Role.ADMIN, "admin@test.com"));

        verify(userRepository, never()).save(any());
        }

        @Test
        void updateUser_whenDemotingAdmin_shouldReject() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(
            user(id, "Admin", "admin@test.com", "hash", Role.ADMIN)));

        assertThrows(BusinessRuleException.class, () ->
            adminUserService.updateUser(id, null, null, Role.PROVIDER, "admin@test.com"));

        verify(userRepository, never()).save(any());
        }

    @Test
    void deleteUser_whenExists_shouldCallDelete() {
        UUID id = UUID.randomUUID();
        UserAccount user = user(id, "To Delete", "del@test.com", "hash", Role.CUSTOMER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        adminUserService.deleteUser(id, "admin@test.com");

        verify(userRepository).deleteById(id);
    }

    @Test
    void deleteUser_whenTargetIsAdmin_shouldReject() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(
                user(id, "Admin", "admin@test.com", "hash", Role.ADMIN)));

        assertThrows(BusinessRuleException.class, () -> adminUserService.deleteUser(id, "admin@test.com"));

        verify(userRepository, never()).deleteById(id);
    }

    private static UserAccount user(UUID id, String name, String email, String password, Role role) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UserAccount(id, name, email, role, null, password, null, null, Status.ACTIVE, now, now);
    }
}

