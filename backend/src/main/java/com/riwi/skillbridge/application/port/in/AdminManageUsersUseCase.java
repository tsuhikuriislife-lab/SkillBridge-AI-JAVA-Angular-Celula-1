package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.List;
import java.util.UUID;

public interface AdminManageUsersUseCase {
    List<UserAccount> listAllUsers();
    UserAccount getUserById(UUID id);
    UserAccount createUser(String name, String email, String password, Role role, String actorEmail);
    UserAccount updateUser(UUID id, String name, String email, Role role, String actorEmail);
    void deleteUser(UUID id, String actorEmail);
}

