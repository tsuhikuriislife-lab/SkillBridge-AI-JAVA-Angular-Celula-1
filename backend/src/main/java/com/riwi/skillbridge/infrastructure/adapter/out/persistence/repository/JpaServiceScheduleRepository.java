package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.ServiceScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaServiceScheduleRepository extends JpaRepository<ServiceScheduleEntity, UUID> {
    List<ServiceScheduleEntity> findByServiceId(UUID serviceId);
}
