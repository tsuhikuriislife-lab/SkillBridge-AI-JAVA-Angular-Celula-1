package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.AdminActivityRepositoryPort;
import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.AdminActivityEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaAdminActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class AdminActivityPersistenceAdapter implements AdminActivityRepositoryPort {
    private final JpaAdminActivityRepository repository;

    public AdminActivityPersistenceAdapter(JpaAdminActivityRepository repository) {
        this.repository = repository;
    }

    @Override
    public AdminActivity save(AdminActivity activity) {
        AdminActivityEntity saved = repository.save(toEntity(activity));
        return toDomain(saved);
    }

    @Override
    public PageResult<AdminActivity> findPageByActorId(java.util.UUID actorId, int page, int size) {
        var result = repository.findByActorIdOrderByCreatedAtDesc(actorId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalPages(), result.getTotalElements(), result.getNumber());
    }

    private AdminActivityEntity toEntity(AdminActivity activity) {
        return new AdminActivityEntity(activity.id(), activity.actorId(), activity.action(),
                activity.targetType(), activity.targetId(), activity.message(), activity.createdAt());
    }

    private AdminActivity toDomain(AdminActivityEntity entity) {
        return new AdminActivity(entity.getId(), entity.getActorId(), entity.getAction(),
                entity.getTargetType(), entity.getTargetId(), entity.getMessage(), entity.getCreatedAt());
    }
}
