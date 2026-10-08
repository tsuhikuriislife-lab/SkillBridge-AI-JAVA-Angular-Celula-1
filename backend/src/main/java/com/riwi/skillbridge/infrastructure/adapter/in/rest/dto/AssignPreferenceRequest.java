package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPreferenceRequest(
    @NotNull UUID preferenceId
) {}
