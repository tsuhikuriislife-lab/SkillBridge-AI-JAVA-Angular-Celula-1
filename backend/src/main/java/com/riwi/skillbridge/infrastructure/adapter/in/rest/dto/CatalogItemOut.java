package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Status;

import java.util.UUID;

public record CatalogItemOut(
    UUID id,
    String name,
    String code,
    String detail,
    CatalogType type,
    Status status
) {}
