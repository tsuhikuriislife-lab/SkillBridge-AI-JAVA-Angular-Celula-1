package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.catalog.CreateCatalogItemUseCase;
import com.riwi.skillbridge.application.port.in.catalog.DeleteCatalogItemUseCase;
import com.riwi.skillbridge.application.port.in.catalog.RetrieveCatalogItemUseCase;
import com.riwi.skillbridge.application.port.in.catalog.UpdateCatalogItemUseCase;
import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.CatalogItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogItemService implements CreateCatalogItemUseCase, RetrieveCatalogItemUseCase,
    UpdateCatalogItemUseCase, DeleteCatalogItemUseCase {

    private final CatalogItemPort catalogItemPort;

    public CatalogItemService(CatalogItemPort catalogItemPort) {
        this.catalogItemPort = catalogItemPort;
    }

    @Override
    public CatalogItem createCatalogItem(CatalogItem catalogItem) {
        validateName(catalogItem.name());
        // 🛡️ REGLA 4: nombre único por tipo
        if (catalogItemPort.existsByNameAndType(catalogItem.name(), catalogItem.type())) {
            throw new BusinessRuleException(
                "Ya existe un " + catalogItem.type() + " con el nombre: " + catalogItem.name());
        }
        // Nace ACTIVE por defecto
        return catalogItemPort.save(new CatalogItem(
            catalogItem.id(),
            catalogItem.name().trim(),
            catalogItem.code(),
            catalogItem.detail(),
            catalogItem.type(),
            Status.ACTIVE
        ));
    }

    @Override
    public Optional<CatalogItem> getCatalogItemById(UUID id) {
        return catalogItemPort.findById(id);
    }

    @Override
    public Optional<CatalogItem> getCatalogItemByCode(String code) {
        return catalogItemPort.findByCode(code);
    }

    @Override
    public List<CatalogItem> getAllByType(CatalogType type) {
        return catalogItemPort.findAllByType(type);
    }

    @Override
    public List<CatalogItem> searchByName(String name) {
        validateName(name);  // 🛡️ búsqueda en blanco = error
        return catalogItemPort.searchByName(name.trim());
    }

    @Override
    public Optional<CatalogItem> updateCatalogItem(UUID id, CatalogItem catalogItem) {
        validateName(catalogItem.name());
        CatalogItem existing = catalogItemPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Item no encontrado con id: " + id));

        // 🛡️ REGLA 4: si cambió el nombre, validar que no choque con otro del mismo tipo
        if (!existing.name().equalsIgnoreCase(catalogItem.name())
            && catalogItemPort.existsByNameAndType(catalogItem.name(), existing.type())) {
            throw new BusinessRuleException(
                "Ya existe otro " + existing.type() + " con el nombre: " + catalogItem.name());
        }

        // code y type no se tocan 🔒 — solo name, detail, status
        return Optional.of(catalogItemPort.save(new CatalogItem(
            existing.id(),
            catalogItem.name().trim(),
            existing.code(),
            catalogItem.detail(),
            existing.type(),
            catalogItem.status() != null ? catalogItem.status() : existing.status()
        )));
    }

    @Override
    public boolean deleteCatalogItem(UUID id) {
        CatalogItem existing = catalogItemPort.findById(id)
            .orElseThrow(() -> new DomainNotFoundException("Item no encontrado con id: " + id));

        // 🛡️ REGLA 3: NO se borra — solo se desactiva
        catalogItemPort.save(new CatalogItem(
            existing.id(),
            existing.name(),
            existing.code(),
            existing.detail(),
            existing.type(),
            Status.INACTIVE
        ));
        return true;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("El nombre no puede estar en blanco");
        }
    }
}
