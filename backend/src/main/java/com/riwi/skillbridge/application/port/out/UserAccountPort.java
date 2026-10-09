// application/port/out/UserAccountPort.java
package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.UserAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountPort {

    UserAccount save(UserAccount userAccount);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserAccount> findAll();

    com.riwi.skillbridge.domain.model.PageResult<UserAccount> findAll(int page, int size);

    void deleteById(UUID id);

    List<UserAccount> searchByName(String name);

    List<UserAccount> searchByEmail(String email);

    List<UserAccount> findAllSortedByName();

    List<UserAccount> findByServiceId(UUID serviceId);

}
