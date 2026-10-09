// UpdateUserAccountUseCase.java
package com.riwi.skillbridge.application.port.in.useraccount;

import com.riwi.skillbridge.domain.enums.Gender;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UpdateUserAccountUseCase {
    Optional<UserAccount> updateUserAccount(UUID id, String name, String image, Gender gender, LocalDate birthDate);
}
