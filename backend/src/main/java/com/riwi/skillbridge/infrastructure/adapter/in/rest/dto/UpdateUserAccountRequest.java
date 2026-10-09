package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.Gender;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UpdateUserAccountRequest(
    @NotBlank(message = "El nombre no puede estar en blanco")
    String name,
    String image,
    Gender gender,
    LocalDate birthDate
) {}
