package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AdminManageOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AdminOfferingService implements AdminManageOfferingsUseCase {
    private final OfferingRepositoryPort offeringRepository;
    private final OfferingCachePort offeringCache;

    public AdminOfferingService(OfferingRepositoryPort offeringRepository, OfferingCachePort offeringCache) {
        this.offeringRepository = offeringRepository;
        this.offeringCache = offeringCache;
    }

    @Override
    public List<Offering> listAllOfferings() {
        return offeringRepository.findAll();
    }

    @Override
    public Offering createOffering(String title, String description, String category, BigDecimal price) {
        Offering offering = new Offering(
                UUID.randomUUID(),
                title != null ? title.trim() : "Nuevo Servicio",
                description != null ? description.trim() : "",
                category != null ? category.trim() : "GENERAL",
                price != null ? price : BigDecimal.ZERO,
                true,
                null,
                null,
                null,
                null,
                null
        );
        Offering saved = offeringRepository.save(offering);
        offeringCache.evictActiveOfferings();
        return saved;
    }

    @Override
    public Offering updateOffering(UUID id, String title, String description, String category, BigDecimal price) {
        Offering current = offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));

        Offering updated = new Offering(
                current.id(),
                (title != null && !title.isBlank()) ? title.trim() : current.title(),
                (description != null) ? description.trim() : current.description(),
                (category != null && !category.isBlank()) ? category.trim() : current.category(),
                (price != null) ? price : current.price(),
                current.active(),
                current.providerId(),
                current.startTime(),
                current.endTime(),
                current.endDay(),
                current.photoUrl()
        );
        Offering saved = offeringRepository.save(updated);
        offeringCache.evictActiveOfferings();
        return saved;
    }

    @Override
    public Offering toggleStatus(UUID id) {
        Offering current = offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));

        Offering updated = new Offering(
                current.id(),
                current.title(),
                current.description(),
                current.category(),
                current.price(),
                !current.active(),
                current.providerId(),
                current.startTime(),
                current.endTime(),
                current.endDay(),
                current.photoUrl()
        );
        Offering saved = offeringRepository.save(updated);
        offeringCache.evictActiveOfferings();
        return saved;
    }

    @Override
    public void deleteOffering(UUID id) {
        offeringRepository.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado con ID: " + id));
        offeringRepository.deleteById(id);
        offeringCache.evictActiveOfferings();
    }
}
