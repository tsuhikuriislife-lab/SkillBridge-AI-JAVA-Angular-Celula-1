package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminManageOfferingsUseCase;
import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.enums.CatalogType;
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
import java.util.UUID;

@Service
public class AdminOfferingService implements AdminManageOfferingsUseCase {
    private final OfferingRepositoryPort offeringRepository;
    private final CatalogItemPort catalogItemPort;
    private final UserAccountPort userAccountPort;
    private final AdminActivityUseCase adminActivity;

    public AdminOfferingService(OfferingRepositoryPort offeringRepository, CatalogItemPort catalogItemPort,
                                UserAccountPort userAccountPort, AdminActivityUseCase adminActivity) {
        this.offeringRepository = offeringRepository;
        this.catalogItemPort = catalogItemPort;
        this.userAccountPort = userAccountPort;
        this.adminActivity = adminActivity;
    }

    @Override
    public List<Offering> listAllOfferings() {
        return offeringRepository.findAll();
    }

    @Override
        public Offering createOffering(String title, String description, String category, BigDecimal price,
                                       String adminEmail) {
        CatalogItem categoryItem = resolveCategory(category);
        UUID adminId = userAccountPort.findByEmail(adminEmail)
            .orElseThrow(() -> new DomainNotFoundException("Administrador no encontrado")).id();
        OffsetDateTime now = OffsetDateTime.now();
        String code = "SRV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Offering offering = new Offering(
                UUID.randomUUID(),
            requireText(title, "El título es obligatorio"),
            categoryItem.id(),
                price != null ? price : BigDecimal.ZERO,
            description,
            description,
            null,
            null,
            10,
            code,
            ServiceStatus.ACTIVE,
            adminId,
            now,
            now
        );
        Offering saved = offeringRepository.save(offering);
        adminActivity.record(adminEmail, "OFFERING_CREATED", "OFFERING", saved.id(),
            "Se creó el servicio " + saved.name() + ".");
        return saved;
    }

    @Override
    public Offering updateOffering(UUID id, String title, String description, String category, BigDecimal price,
                                   String actorEmail) {
        Offering current = offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));

        UUID categoryId = category != null && !category.isBlank()
            ? resolveCategory(category).id() : current.categoryId();
        Offering updated = new Offering(
                current.id(),
            (title != null && !title.isBlank()) ? title.trim() : current.name(),
            categoryId,
                (price != null) ? price : current.price(),
            (description != null) ? description : current.detail(),
            (description != null) ? description : current.shortDescription(),
            current.learningObjectives(),
            current.prerequisites(),
            current.capacity(),
            current.code(),
            current.status(),
            current.createdBy(),
            current.createdAt(),
            OffsetDateTime.now()
        );
        Offering saved = offeringRepository.save(updated);
        adminActivity.record(actorEmail, "OFFERING_UPDATED", "OFFERING", saved.id(),
            "Se actualizó el servicio " + saved.name() + ".");
        return saved;
    }

    @Override
    public Offering toggleStatus(UUID id, String actorEmail) {
        Offering current = offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));

        Offering updated = new Offering(
                current.id(),
            current.name(),
            current.categoryId(),
                current.price(),
            current.detail(),
            current.shortDescription(),
            current.learningObjectives(),
            current.prerequisites(),
            current.capacity(),
            current.code(),
            current.status() == ServiceStatus.ACTIVE ? ServiceStatus.INACTIVE : ServiceStatus.ACTIVE,
            current.createdBy(),
            current.createdAt(),
            OffsetDateTime.now()
        );
        Offering saved = offeringRepository.save(updated);
        adminActivity.record(actorEmail, "OFFERING_STATUS_CHANGED", "OFFERING", saved.id(),
            "Se cambió el estado de " + saved.name() + " a " + saved.status() + ".");
        return saved;
    }

    @Override
    public void deleteOffering(UUID id, String actorEmail) {
        Offering offering = offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));
        offeringRepository.deleteById(id);
        adminActivity.record(actorEmail, "OFFERING_DELETED", "OFFERING", offering.id(),
                "Se eliminó el servicio " + offering.name() + ".");
    }

    private CatalogItem resolveCategory(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("La categoría es obligatoria");
        }
        CatalogItem category = catalogItemPort.findByCode(value.trim())
                .orElseGet(() -> catalogItemPort.searchByName(value.trim()).stream()
                        .filter(item -> item.type() == CatalogType.CATEGORY)
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("La categoría no existe: " + value)));
        if (category.type() != CatalogType.CATEGORY || category.status() != Status.ACTIVE) {
            throw new BusinessRuleException("La categoría no existe o está inactiva: " + value);
        }
        return category;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessRuleException(message);
        return value.trim();
    }
}
