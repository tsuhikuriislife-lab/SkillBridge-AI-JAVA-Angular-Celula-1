package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserPreferenceEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.UserPreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaUserPreferenceRepository
    extends JpaRepository<UserPreferenceEntity, UserPreferenceId> {

    List<UserPreferenceEntity> findByUserIdAndStatus(UUID userId, Status status);

    List<UserPreferenceEntity> findByUserId(UUID userId);

    @Query("""
        SELECT new com.riwi.skillbridge.application.model.UserPreferenceView(
            c.id, c.name, c.code, c.detail,
            CASE WHEN up.status = 'ACTIVE' THEN true ELSE false END
        )
        FROM CatalogItemEntity c
        LEFT JOIN UserPreferenceEntity up
               ON up.preferenceId = c.id AND up.userId = :userId
        WHERE c.type = com.riwi.skillbridge.domain.enums.CatalogType.PREFERENCE
          AND c.status = com.riwi.skillbridge.domain.enums.Status.ACTIVE
        ORDER BY c.name
        """)
    List<UserPreferenceView> findAllPreferencesWithAssignment(@Param("userId") UUID userId);
}
