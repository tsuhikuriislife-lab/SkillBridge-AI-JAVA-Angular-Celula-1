package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.Status;

import java.util.UUID;

public record UserPreference(
    UUID userId,
    UUID preferenceId,
    Status status
) {}
