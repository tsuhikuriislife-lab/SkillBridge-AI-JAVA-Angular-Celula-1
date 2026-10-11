package com.riwi.skillbridge.application.port.in.useraccount;

import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrieveUserAccountUseCase {
    Optional<UserAccount> getUserAccountById(UUID id);
    List<UserAccount> getAllUserAccounts();

    PageResult<UserAccount> getAllUserAccounts(int page, int size);
    List<UserAccount> searchByName(String name);
    List<UserAccount> searchByEmail(String email);
    List<UserAccount> getAllSortedByName();
    List<UserAccount> getUsersByService(UUID serviceId);
}
