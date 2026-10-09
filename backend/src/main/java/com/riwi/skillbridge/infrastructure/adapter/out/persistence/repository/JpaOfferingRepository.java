package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.OfferingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaOfferingRepository extends JpaRepository<OfferingEntity, UUID>,
        JpaSpecificationExecutor<OfferingEntity> {
    Optional<OfferingEntity> findByCode(String code);
    List<OfferingEntity> findByCreatedBy(UUID createdBy);
    List<OfferingEntity> findByStatus(ServiceStatus status);
}
