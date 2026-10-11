package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.UUID;

public interface AdminActivityRepositoryPort {
    AdminActivity save(AdminActivity activity);
    PageResult<AdminActivity> findPageByActorId(UUID actorId, int page, int size);
}
