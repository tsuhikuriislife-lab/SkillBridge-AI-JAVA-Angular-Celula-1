package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.ServiceStatus;
import jakarta.validation.constraints.NotNull;

public record ServiceStatusRequest(@NotNull ServiceStatus status) {}
