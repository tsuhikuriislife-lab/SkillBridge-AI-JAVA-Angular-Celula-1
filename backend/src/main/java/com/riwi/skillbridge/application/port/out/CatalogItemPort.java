package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.model.CatalogItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemPort {

    CatalogItem save(CatalogItem catalogItem);

    Optional<CatalogItem> findById(UUID id);

    Optional<CatalogItem> findByCode(String code);

    List<CatalogItem> findAllByType(CatalogType type);

    List<CatalogItem> searchByName(String name);

    boolean existsByNameAndType(String name, CatalogType type);

    void deleteById(UUID id);

}
