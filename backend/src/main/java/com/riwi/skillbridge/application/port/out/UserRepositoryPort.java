package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import java.util.List;

public interface UserRepositoryPort extends UserAccountPort {
    List<UserAccount> findByRole(Role role);
    long countByRole(Role role);
}
