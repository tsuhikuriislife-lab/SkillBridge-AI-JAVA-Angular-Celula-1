package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    boolean existsByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findById(UUID id);
    List<UserAccount> findAll();
    List<UserAccount> findByRole(Role role);
    UserAccount save(UserAccount user);
    void deleteById(UUID id);
}
