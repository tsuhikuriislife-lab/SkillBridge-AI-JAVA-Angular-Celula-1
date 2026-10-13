package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String name,
        String fullName,
        String email,
        String role,
        boolean active,
        String status
) {}

