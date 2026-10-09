package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> {
    List<OfferingEntity> findAllByOrderByTitleAsc();
    List<OfferingEntity> findByActiveTrueOrderByTitleAsc();
    
    Page<OfferingEntity> findByProviderId(UUID providerId, Pageable pageable);

    long countByProviderId(UUID providerId);
    
    @Query("SELECT DISTINCT o.category FROM OfferingEntity o ORDER BY o.category ASC")
    List<String> findDistinctCategories();

    @Query("SELECT new com.riwi.skillbridge.domain.model.CategoryCount(o.category, COUNT(o)) FROM OfferingEntity o GROUP BY o.category ORDER BY COUNT(o) DESC")
    List<com.riwi.skillbridge.domain.model.CategoryCount> findTopCategories(Pageable pageable);
}
