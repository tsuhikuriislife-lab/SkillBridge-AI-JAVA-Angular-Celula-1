package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PasswordHasherPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, passwordHasher);
    }

    @Test
    void listAllUsers_shouldReturnList() {
        UserAccount user = new UserAccount(UUID.randomUUID(), "Juan", "juan@test.com", "hash", Role.CUSTOMER);
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserAccount> result = adminUserService.listAllUsers();

        assertEquals(1, result.size());
        assertEquals("Juan", result.getFirst().name());
    }

    @Test
    void getUserById_whenExists_shouldReturnUser() {
        UUID id = UUID.randomUUID();
        UserAccount user = new UserAccount(id, "Admin", "admin@test.com", "hash", Role.ADMIN);
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
                adminUserService.createUser("Test", "test@test.com", "pass", Role.CUSTOMER));
    }

    @Test
    void createUser_success() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordHasher.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = adminUserService.createUser("New User", "new@test.com", "pass", Role.CUSTOMER);

        assertNotNull(created);
        assertEquals("New User", created.name());
        assertEquals("new@test.com", created.email());
        assertEquals("encoded", created.passwordHash());
        assertEquals(Role.CUSTOMER, created.role());
    }

    @Test
    void deleteUser_whenExists_shouldCallDelete() {
        UUID id = UUID.randomUUID();
        UserAccount user = new UserAccount(id, "To Delete", "del@test.com", "hash", Role.CUSTOMER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        adminUserService.deleteUser(id);

        verify(userRepository).deleteById(id);
    }
}

