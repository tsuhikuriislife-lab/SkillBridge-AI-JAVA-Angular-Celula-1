package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingHistoryPort;
import com.riwi.skillbridge.domain.model.BookingHistory;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingHistoryEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingHistoryRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class BookingHistoryPersistenceAdapter implements BookingHistoryPort {

    private final JpaBookingHistoryRepository repository;

    public BookingHistoryPersistenceAdapter(JpaBookingHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public BookingHistory save(BookingHistory h) {
        return toDomain(repository.save(new BookingHistoryEntity(h.id(), h.userId(), h.serviceId(),
                h.status(), h.createdAt(), h.expiresAt())));
    }

    @Override
    public List<BookingHistory> findByUserIdAndServiceId(UUID userId, UUID serviceId) {
        return repository.findByUserIdAndServiceIdOrderByCreatedAtAsc(userId, serviceId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public com.riwi.skillbridge.domain.model.PageResult<BookingHistory> findByUserId(UUID userId, int page, int size) {
        var p = repository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(page, size));
        return new com.riwi.skillbridge.domain.model.PageResult<>(
                p.getContent().stream().map(this::toDomain).toList(),
                p.getTotalPages(), p.getTotalElements(), p.getNumber());
    }

    private BookingHistory toDomain(BookingHistoryEntity e) {
        return new BookingHistory(e.getId(), e.getUserId(), e.getServiceId(),
                e.getStatus(), e.getCreatedAt(), e.getExpiresAt());
    }
}
