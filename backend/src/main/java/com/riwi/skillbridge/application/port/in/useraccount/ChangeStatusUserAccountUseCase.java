// ChangeStatusUserAccountUseCase.java
package com.riwi.skillbridge.application.port.in.useraccount;

import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface ChangeStatusUserAccountUseCase {
    Optional<UserAccount> changeStatus(UUID id, boolean active);
}
