package com.riwi.skillbridge.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {
    private final JpaBookingRepository repository;

    public BookingPersistenceAdapter(JpaBookingRepository repository) { this.repository = repository; }

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = new BookingEntity(
                booking.id(), booking.offeringId(), booking.customerId(), booking.scheduledAt(), booking.status(), Instant.now());
        BookingEntity saved = repository.save(entity);
        return new Booking(saved.getId(), saved.getOfferingId(), saved.getCustomerId(), saved.getScheduledAt(), saved.getStatus());
    }

    @Override
    public List<Booking> findAll() {
        return List.of();
    }
}

 // 2.
//Adaptador de persistencia (BookingPersistenceAdapter): usa el findAll() que ya hereda de JpaRepository y convierte cada entidad a Booking:
//@Override
//public List<Booking> findAll() {
//    return repository.findAll().stream()
//        .map(entity -> new Booking(
//            entity.getId(),
//            entity.getOfferingId(),
//            entity.getCustomerId(),
//            entity.getScheduledAt(),
//            entity.getStatus()))
//        .toList();
//}


//3.
//Servicio (BookingService): delega en el puerto, en vez de devolver una lista vacía:
//@Override
//public List<Booking> findAll() {
//    return bookingRepository.findAll();
//}
