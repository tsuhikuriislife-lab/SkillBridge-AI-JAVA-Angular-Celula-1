package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.CatalogItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCatalogItemRepository extends JpaRepository<CatalogItemEntity, UUID> {

    Optional<CatalogItemEntity> findByCode(String code);

    List<CatalogItemEntity> findAllByType(CatalogType type);

    List<CatalogItemEntity> findAllByTypeOrderByNameAsc(CatalogType type);

    List<CatalogItemEntity> findByNameContainingIgnoreCase(String name);

    // Regla 4: validar duplicado por nombre dentro del mismo tipo
    boolean existsByNameIgnoreCaseAndType(String name, CatalogType type);
}
