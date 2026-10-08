package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OfferingPersistenceAdapter implements OfferingRepositoryPort {
    private final JpaOfferingRepository repository;

    public OfferingPersistenceAdapter(JpaOfferingRepository repository) { this.repository = repository; }

    @Override
    public List<Offering> findAllActive() {
        return repository.findByStatusOrderByNameAsc(OfferingStatus.ACTIVE).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Offering> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public OfferingPage findPageByProviderId(UUID providerId, int page, int size, OfferingSort sort) {
        Sort order = switch (sort) {
            case NAME_ASC -> Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));
            case NAME_DESC -> Sort.by(Sort.Order.desc("name").ignoreCase(), Sort.Order.asc("id"));
            case CREATED_ASC -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case CREATED_DESC -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        };
        var result = repository.findByCreatedBy(providerId, PageRequest.of(page, size, order));
        var content = result.getContent().stream().map(this::toDomain).toList();
        return new OfferingPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public Offering save(Offering offering) {
        OfferingEntity entity = repository.findById(offering.id())
                .map(existing -> {
                    existing.update(offering.name(), offering.categoryId(), offering.price(),
                            offering.shortDescription(), offering.detail(), offering.learningObjectives(),
                            offering.prerequisites(), offering.capacity());
                    existing.changeStatus(offering.status());
                    return existing;
                })
                .orElseGet(() -> new OfferingEntity(offering.id(), offering.code(), offering.name(),
                        offering.categoryId(), offering.price(), offering.shortDescription(), offering.detail(),
                        offering.learningObjectives(), offering.prerequisites(), offering.capacity(),
                        offering.status(), offering.createdBy()));
        return toDomain(repository.save(entity));
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(e.getId(), e.getCode(), e.getName(), e.getCategoryId(), e.getPrice(),
                e.getShortDescription(), e.getDetail(), e.getLearningObjectives(), e.getPrerequisites(),
                e.getCapacity(), e.getStatus(), e.getCreatedBy());
    }
}