package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Status;

import java.util.UUID;

public record CatalogItem(
    UUID id,
    String name,
    String code,
    String detail,
    CatalogType type,
    Status status
) {}
