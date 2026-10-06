package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BookingService implements CreateBookingUseCase  {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserAccountPort userAccountPort;
    private final BookingEventPublisherPort eventPublisher;

    public BookingService(BookingRepositoryPort bookingRepository,
                          OfferingRepositoryPort offeringRepository,
                          UserAccountPort userAccountPort,
                          BookingEventPublisherPort eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.offeringRepository = offeringRepository;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }

        Offering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!offering.active()) {
            throw new BusinessRuleException("El servicio no está activo");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
                .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customerId, scheduledAt, BookingStatus.CREATED);
        Booking saved = bookingRepository.save(booking);
        eventPublisher.bookingCreated(saved);
        return saved;
    }
}
