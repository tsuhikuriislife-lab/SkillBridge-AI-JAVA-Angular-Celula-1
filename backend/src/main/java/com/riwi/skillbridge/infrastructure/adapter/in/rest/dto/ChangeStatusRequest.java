package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import org.jetbrains.annotations.NotNull;

public record ChangeStatusRequest  (
    @NotNull boolean active
) { }
