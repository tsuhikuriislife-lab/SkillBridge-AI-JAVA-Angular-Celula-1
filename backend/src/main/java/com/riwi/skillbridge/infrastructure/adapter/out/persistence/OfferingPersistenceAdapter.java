package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OfferingPersistenceAdapter implements OfferingPort {

    private final JpaOfferingRepository repository;

    public OfferingPersistenceAdapter(JpaOfferingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Offering save(Offering o) {
        return toDomain(repository.save(new OfferingEntity(o.id(), o.name(), o.categoryId(), o.price(),
                o.detail(), o.shortDescription(), o.learningObjectives(), o.prerequisites(),
                o.capacity(), o.code(), o.status(), o.createdBy(), o.createdAt(), o.updatedAt())));
    }

    @Override
    public Optional<Offering> findById(UUID id) { return repository.findById(id).map(this::toDomain); }

    @Override
    public Optional<Offering> findByCode(String code) { return repository.findByCode(code).map(this::toDomain); }

    @Override
    public List<Offering> findByCreatedBy(UUID createdBy) {
        return repository.findByCreatedBy(createdBy).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Offering> findAll() { return repository.findAll().stream().map(this::toDomain).toList(); }

    @Override
    public List<Offering> findAllActive() {
        return repository.findByStatus(ServiceStatus.ACTIVE).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<Offering> findCatalog(String name, UUID categoryId, Boolean freeOnly,
                                            int page, int size, String sort) {
        Specification<OfferingEntity> spec = (root, q, cb) -> cb.conjunction();
        spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), ServiceStatus.ACTIVE));
        if (name != null && !name.isBlank())
            spec = spec.and((root, q, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        if (categoryId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("categoryId"), categoryId));
        if (freeOnly != null)
            spec = spec.and((root, q, cb) -> freeOnly
                    ? cb.equal(root.get("price"), BigDecimal.ZERO)
                    : cb.greaterThan(root.get("price"), BigDecimal.ZERO));
        Page<OfferingEntity> result = repository.findAll(spec, PageRequest.of(page, size, parseSort(sort)));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalPages(), result.getTotalElements(), result.getNumber());
    }

    @Override
    public void deleteById(UUID id) { repository.deleteById(id); }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by("name").ascending();
        String[] parts = sort.split(",");
        return "desc".equalsIgnoreCase(parts.length > 1 ? parts[1] : "asc")
                ? Sort.by(parts[0]).descending() : Sort.by(parts[0]).ascending();
    }

    private Offering toDomain(OfferingEntity e) {
        return new Offering(e.getId(), e.getName(), e.getCategoryId(), e.getPrice(), e.getDetail(),
                e.getShortDescription(), e.getLearningObjectives(), e.getPrerequisites(),
                e.getCapacity(), e.getCode(), e.getStatus(), e.getCreatedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
