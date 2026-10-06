package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID> {
    List<OfferingEntity> findByActiveTrueOrderByTitleAsc();
    
    Page<OfferingEntity> findByProviderId(UUID providerId, Pageable pageable);
    
    @Query("SELECT DISTINCT o.category FROM OfferingEntity o ORDER BY o.category ASC")
    List<String> findDistinctCategories();
}
