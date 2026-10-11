package com.riwi.skillbridge.application.port.in.catalog;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.model.CatalogItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrieveCatalogItemUseCase {
    Optional<CatalogItem> getCatalogItemById(UUID id);
    Optional<CatalogItem> getCatalogItemByCode(String code);
    List<CatalogItem> getAllByType(CatalogType type);
    List<CatalogItem> searchByName(String name);
}
