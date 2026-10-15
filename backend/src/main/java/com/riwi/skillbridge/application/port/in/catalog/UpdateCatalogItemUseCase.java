package com.riwi.skillbridge.application.port.in.catalog;

import com.riwi.skillbridge.domain.model.CatalogItem;

import java.util.Optional;
import java.util.UUID;

public interface UpdateCatalogItemUseCase {
    Optional<CatalogItem> updateCatalogItem(UUID id, CatalogItem catalogItem);
}
