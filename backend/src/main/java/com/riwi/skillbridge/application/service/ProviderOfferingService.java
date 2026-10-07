package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ManageProviderOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ProviderOfferingService implements ManageProviderOfferingsUseCase {
    private final OfferingRepositoryPort repository;
    private final OfferingCachePort cache;
    private final UserRepositoryPort userRepository;

    public ProviderOfferingService(OfferingRepositoryPort repository, OfferingCachePort cache, UserRepositoryPort userRepository) {
        this.repository = repository;
        this.cache = cache;
        this.userRepository = userRepository;
    }

    private UUID getProviderId(String email) {
        return userRepository.findByEmail(email)
                .map(UserAccount::id)
                .orElseThrow(() -> new DomainNotFoundException("User not found"));
    }

    @Override
    public Offering create(String providerEmail, String title, String description, String category, BigDecimal price, LocalTime startTime, LocalTime endTime, String endDay, String photoUrl) {
        UUID providerId = getProviderId(providerEmail);
        Offering offering = new Offering(UUID.randomUUID(), title, description, category, price, true, providerId, startTime, endTime, endDay, photoUrl);
        Offering saved = repository.save(offering);
        cache.evictActiveOfferings();
        return saved;
    }

    @Override
    public PageResult<Offering> getProviderOfferings(String providerEmail, int page, int size) {
        UUID providerId = getProviderId(providerEmail);
        return repository.findByProviderId(providerId, page, size);
    }

    @Override
    public Offering update(String providerEmail, UUID offeringId, Map<String, Object> updates) {
        UUID providerId = getProviderId(providerEmail);
        Offering existing = repository.findById(offeringId)
                .orElseThrow(() -> new DomainNotFoundException("Offering not found"));
        
        if (existing.providerId() == null || !existing.providerId().equals(providerId)) {
            throw new BusinessRuleException("No tienes permiso para modificar este servicio");
        }

        String title = updates.containsKey("title") ? (String) updates.get("title") : existing.title();
        String description = updates.containsKey("description") ? (String) updates.get("description") : existing.description();
        String category = updates.containsKey("category") ? (String) updates.get("category") : existing.category();
        BigDecimal price = updates.containsKey("price") ? new BigDecimal(updates.get("price").toString()) : existing.price();
        
        LocalTime startTime = existing.startTime();
        if (updates.containsKey("startTime")) {
            startTime = updates.get("startTime") != null ? LocalTime.parse(updates.get("startTime").toString()) : null;
        }
        
        LocalTime endTime = existing.endTime();
        if (updates.containsKey("endTime")) {
            endTime = updates.get("endTime") != null ? LocalTime.parse(updates.get("endTime").toString()) : null;
        }

        String endDay = updates.containsKey("endDay") ? (String) updates.get("endDay") : existing.endDay();
        String photoUrl = updates.containsKey("photoUrl") ? (String) updates.get("photoUrl") : existing.photoUrl();

        Offering updated = new Offering(existing.id(), title, description, category, price, existing.active(), providerId, startTime, endTime, endDay, photoUrl);
        Offering saved = repository.save(updated);
        
        if (saved.active()) {
            cache.evictActiveOfferings();
        }
        return saved;
    }

    @Override
    public Offering toggleStatus(String providerEmail, UUID offeringId) {
        UUID providerId = getProviderId(providerEmail);
        Offering existing = repository.findById(offeringId)
                .orElseThrow(() -> new DomainNotFoundException("Offering not found"));
                
        if (existing.providerId() == null || !existing.providerId().equals(providerId)) {
            throw new BusinessRuleException("No tienes permiso para modificar este servicio");
        }

        Offering updated = new Offering(existing.id(), existing.title(), existing.description(), existing.category(), existing.price(), !existing.active(), providerId, existing.startTime(), existing.endTime(), existing.endDay(), existing.photoUrl());
        Offering saved = repository.save(updated);
        cache.evictActiveOfferings();
        return saved;
    }
}
