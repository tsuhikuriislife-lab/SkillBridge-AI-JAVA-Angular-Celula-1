package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Gender;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserAccountOut(
    UUID id,
    String name,
    String email,
    Role role,
    String image,
    Gender gender,
    LocalDate birthDate,
    Status status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
