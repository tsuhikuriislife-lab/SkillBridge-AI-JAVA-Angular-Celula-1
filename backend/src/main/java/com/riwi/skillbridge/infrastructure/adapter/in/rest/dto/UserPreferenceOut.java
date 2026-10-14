package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Status;

import java.util.UUID;

public record UserPreferenceOut(
    UUID userId,
    UUID preferenceId,
    Status status
) {}
