package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.AdminActivityRepositoryPort;
import com.riwi.skillbridge.domain.model.AdminActivity;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.AdminActivityEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaAdminActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AdminActivityPersistenceAdapter implements AdminActivityRepositoryPort {
    private static final Pattern EMAIL_COLON_PATTERN = Pattern.compile("Correo\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern EMAIL_UPDATED_PATTERN = Pattern.compile("Se actualiz[oó]\\s+([^;]+);");
    private static final Pattern PREV_ROLE_PATTERN = Pattern.compile("Rol Anterior\\s*:\\s*(\\w+)");
    private static final Pattern NEXT_ROLE_PATTERN = Pattern.compile("Rol Nuevo\\s*:\\s*(\\w+)");
    private static final Pattern ROLE_ARROW_PATTERN = Pattern.compile("rol:\\s*(\\w+)\\s*->\\s*(\\w+)");
    private static final Pattern SINGLE_ROLE_PATTERN = Pattern.compile("Rol\\s*:\\s*(\\w+)");

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
        String msg = entity.getMessage() != null ? entity.getMessage() : "";
        String userId = entity.getTargetId() != null ? entity.getTargetId().toString() : null;
        String userEmail = null;
        String previousRole = null;
        String newRole = null;

        var emailColonMatcher = EMAIL_COLON_PATTERN.matcher(msg);
        if (emailColonMatcher.find()) {
            userEmail = emailColonMatcher.group(1).trim();
        } else {
            var emailUpdatedMatcher = EMAIL_UPDATED_PATTERN.matcher(msg);
            if (emailUpdatedMatcher.find()) {
                userEmail = emailUpdatedMatcher.group(1).trim();
            }
        }

        var prevMatcher = PREV_ROLE_PATTERN.matcher(msg);
        var nextMatcher = NEXT_ROLE_PATTERN.matcher(msg);
        if (prevMatcher.find() && nextMatcher.find()) {
            previousRole = prevMatcher.group(1).trim();
            newRole = nextMatcher.group(1).trim();
        } else {
            var roleArrowMatcher = ROLE_ARROW_PATTERN.matcher(msg);
            if (roleArrowMatcher.find()) {
                previousRole = roleArrowMatcher.group(1).trim();
                newRole = roleArrowMatcher.group(2).trim();
            } else {
                var singleRoleMatcher = SINGLE_ROLE_PATTERN.matcher(msg);
                if (singleRoleMatcher.find()) {
                    newRole = singleRoleMatcher.group(1).trim();
                }
            }
        }

        return new AdminActivity(
                entity.getId(),
                entity.getActorId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getMessage(),
                entity.getCreatedAt(),
                userId,
                userEmail,
                previousRole,
                newRole
        );
    }
}
