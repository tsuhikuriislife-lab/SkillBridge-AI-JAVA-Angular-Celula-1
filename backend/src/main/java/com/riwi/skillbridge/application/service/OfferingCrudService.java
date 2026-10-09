package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.offering.CreateOfferingUseCase;
import com.riwi.skillbridge.application.port.in.offering.DeleteOfferingUseCase;
import com.riwi.skillbridge.application.port.in.offering.RetrieveOfferingUseCase;
import com.riwi.skillbridge.application.port.in.offering.UpdateOfferingUseCase;
import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OfferingCrudService implements CreateOfferingUseCase, RetrieveOfferingUseCase,
        UpdateOfferingUseCase, DeleteOfferingUseCase {

    private final OfferingPort offeringPort;
    private final UserAccountPort userAccountPort;
    private final CatalogItemPort catalogItemPort;

    public OfferingCrudService(OfferingPort offeringPort, UserAccountPort userAccountPort,
                               CatalogItemPort catalogItemPort) {
        this.offeringPort = offeringPort;
        this.userAccountPort = userAccountPort;
        this.catalogItemPort = catalogItemPort;
    }

    @Override
    public Offering createOffering(Offering offering) {
        validate(offering);
        return offeringPort.save(new Offering(
                offering.id() != null ? offering.id() : UUID.randomUUID(),
                offering.name().trim(), offering.categoryId(), offering.price(),
                offering.detail(), offering.shortDescription(), offering.learningObjectives(),
                offering.prerequisites(), offering.capacity(), offering.code().trim(),
                offering.status() != null ? offering.status() : ServiceStatus.DRAFT,
                offering.createdBy(), OffsetDateTime.now(), OffsetDateTime.now()));
    }

    @Override
    public Optional<Offering> getOfferingById(UUID id) { return offeringPort.findById(id); }

    @Override
    public Optional<Offering> getOfferingByCode(String code) { return offeringPort.findByCode(code); }

    @Override
    public List<Offering> getOfferingsByCreator(UUID createdBy) { return offeringPort.findByCreatedBy(createdBy); }

    @Override
    public List<Offering> getAllOfferings() { return offeringPort.findAll(); }

    @Override
    public Optional<Offering> updateOffering(UUID id, Offering o) {
        Offering existing = offeringPort.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + id));
        Offering merged = new Offering(
                existing.id(),
                o.name() != null ? o.name().trim() : existing.name(),
                o.categoryId() != null ? o.categoryId() : existing.categoryId(),
                o.price() != null ? o.price() : existing.price(),
                o.detail() != null ? o.detail() : existing.detail(),
                o.shortDescription() != null ? o.shortDescription() : existing.shortDescription(),
                o.learningObjectives() != null ? o.learningObjectives() : existing.learningObjectives(),
                o.prerequisites() != null ? o.prerequisites() : existing.prerequisites(),
                o.capacity() != null ? o.capacity() : existing.capacity(),
                existing.code(),                       // 🔒 el code no cambia
                o.status() != null ? o.status() : existing.status(),
                existing.createdBy(),                  // 🔒 el creador no cambia
                existing.createdAt(), OffsetDateTime.now());
        validate(merged);
        return Optional.of(offeringPort.save(merged));
    }

    @Override
    public boolean deleteOffering(UUID id) {
        Offering existing = offeringPort.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con id: " + id));
        // No se borra físicamente: se desactiva
        offeringPort.save(new Offering(existing.id(), existing.name(), existing.categoryId(),
                existing.price(), existing.detail(), existing.shortDescription(),
                existing.learningObjectives(), existing.prerequisites(), existing.capacity(),
                existing.code(), ServiceStatus.INACTIVE, existing.createdBy(),
                existing.createdAt(), OffsetDateTime.now()));
        return true;
    }

    private void validate(Offering o) {
        if (o.name() == null || o.name().isBlank())
            throw new BusinessRuleException("El nombre del servicio no puede estar en blanco");
        if (o.code() == null || o.code().isBlank())
            throw new BusinessRuleException("El código del servicio no puede estar en blanco");
        if (o.price() == null || o.price().compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessRuleException("El precio no puede ser negativo (use 0 para servicios gratuitos)");
        if (o.capacity() == null || o.capacity() <= 0)
            throw new BusinessRuleException("La capacidad debe ser mayor que cero");
        CatalogItem cat = catalogItemPort.findById(o.categoryId())
                .orElseThrow(() -> new BusinessRuleException("La categoría no existe: " + o.categoryId()));
        if (cat.type() != CatalogType.CATEGORY)
            throw new BusinessRuleException("El item '" + cat.name() + "' no es una categoría");
        if (cat.status() != Status.ACTIVE)
            throw new BusinessRuleException("La categoría '" + cat.name() + "' está desactivada");
        var creator = userAccountPort.findById(o.createdBy())
                .orElseThrow(() -> new BusinessRuleException("El proveedor no existe: " + o.createdBy()));
        if (creator.role() != Role.PROVIDER && creator.role() != Role.ADMIN)
            throw new BusinessRuleException("Solo proveedores pueden tener servicios");
        offeringPort.findByCode(o.code()).ifPresent(existing -> {
            if (!existing.id().equals(o.id()))
                throw new BusinessRuleException("Ya existe un servicio con el código: " + o.code());
        });
    }
}
