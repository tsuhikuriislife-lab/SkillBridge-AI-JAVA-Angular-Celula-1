package com.riwi.skillbridge.application.port.in.useraccount;

import java.util.UUID;

public interface DeleteUserAccountUseCase {
    boolean deleteUserAccount(UUID id);
}
