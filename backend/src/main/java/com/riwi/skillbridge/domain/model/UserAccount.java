package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.Gender;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.Status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserAccount(
    UUID id,
    String name,
    String email,
    Role role,
    String image,
    String password,
    Gender gender,
    LocalDate birthDate,
    Status status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
