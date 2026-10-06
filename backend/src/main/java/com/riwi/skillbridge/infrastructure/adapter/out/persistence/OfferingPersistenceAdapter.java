package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Component
public class OfferingPersistenceAdapter implements OfferingRepositoryPort {
    private final JpaOfferingRepository repository;

    public OfferingPersistenceAdapter(JpaOfferingRepository repository) { this.repository = repository; }

    @Override
    public List<Offering> findAllActive() {
        return repository.findByActiveTrueOrderByTitleAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Offering> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Offering save(Offering offering) {
        OfferingEntity entity = new OfferingEntity(
            offering.id(), offering.title(), offering.description(), offering.category(),
            offering.price(), offering.active(), offering.providerId(), offering.startTime(),
            offering.endTime(), offering.endDay(), offering.photoUrl()
        );
        return toDomain(repository.save(entity));
    }

    @Override
    public PageResult<Offering> findByProviderId(UUID providerId, int page, int size) {
        Page<OfferingEntity> entityPage = repository.findByProviderId(providerId, PageRequest.of(page, size));
        List<Offering> offerings = entityPage.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(offerings, entityPage.getTotalPages(), entityPage.getTotalElements(), entityPage.getNumber());
    }

    @Override
    public List<String> findDistinctCategories() {
        return repository.findDistinctCategories();
    }

    @Override
    public List<com.riwi.skillbridge.domain.model.CategoryCount> findTopCategories(int limit) {
        return repository.findTopCategories(PageRequest.of(0, limit));
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(e.getId(), e.getTitle(), e.getDescription(), e.getCategory(), e.getPrice(), e.isActive(), e.getProviderId(), e.getStartTime(), e.getEndTime(), e.getEndDay(), e.getPhotoUrl());
    }
}
