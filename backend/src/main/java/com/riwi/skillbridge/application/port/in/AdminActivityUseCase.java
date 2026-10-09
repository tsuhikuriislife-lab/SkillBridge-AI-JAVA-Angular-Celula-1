package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;

import java.util.UUID;

public interface AdminActivityUseCase {
    void record(String actorEmail, String action, String targetType, UUID targetId, String message);
    PageResult<AdminActivity> listMine(String actorEmail, int page, int size);
}
