package com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository;

import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaBookingHistoryRepository extends JpaRepository<BookingHistoryEntity, UUID> {
    List<BookingHistoryEntity> findByUserIdAndServiceIdOrderByCreatedAtAsc(UUID userId, UUID serviceId);
    org.springframework.data.domain.Page<BookingHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, org.springframework.data.domain.Pageable pageable);
}
