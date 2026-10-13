package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

public record UpdateAdminUserRequest(
        String name,
        String email,
        String role
) {}

