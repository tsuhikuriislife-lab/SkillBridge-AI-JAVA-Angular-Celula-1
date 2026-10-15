package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.CatalogItemEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaCatalogItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CatalogItemPersistenceAdapter implements CatalogItemPort {

    private final JpaCatalogItemRepository repository;

    public CatalogItemPersistenceAdapter(JpaCatalogItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public CatalogItem save(CatalogItem catalogItem) {
        return toDomain(repository.save(toEntity(catalogItem)));
    }

    @Override
    public Optional<CatalogItem> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<CatalogItem> findByCode(String code) {
        return repository.findByCode(code).map(this::toDomain);
    }

    @Override
    public List<CatalogItem> findAllByType(CatalogType type) {
        return repository.findAllByTypeOrderByNameAsc(type).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CatalogItem> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByNameAndType(String name, CatalogType type) {
        return repository.existsByNameIgnoreCaseAndType(name, type);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private CatalogItemEntity toEntity(CatalogItem c) {
        return new CatalogItemEntity(c.id(), c.name(), c.code(), c.detail(), c.type(), c.status());
    }

    private CatalogItem toDomain(CatalogItemEntity e) {
        return new CatalogItem(e.getId(), e.getName(), e.getCode(), e.getDetail(), e.getType(), e.getStatus());
    }
}
