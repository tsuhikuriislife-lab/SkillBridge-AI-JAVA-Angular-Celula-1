package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.application.model.CurrentUser;

public interface GetCurrentUserUseCase {
    CurrentUser getByEmail(String email);
}