package com.riwi.skillbridge.infrastructure.adapter.out.persistence;


import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaOfferingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaBookingRepository;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.BookingEntity;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.OfferingStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaOfferingRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("skillbridge_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JpaOfferingRepository repository;

    @Autowired
    JpaBookingRepository bookingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayShouldSeedThreeActiveOfferings() {
        assertThat(repository.findByStatusOrderByNameAsc(OfferingStatus.ACTIVE)).hasSize(3);
    }

        @Test
        void shouldPersistAndFilterPagedBookingsByActivityAndSort() {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO app_users (id, name, email, password, role) VALUES (?, ?, ?, ?, ?)",
            customerId, "Test Customer", customerId + "@example.com", "unused", "CUSTOMER"
        );

        UUID javaOfferingId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID angularOfferingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID cloudOfferingId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant scheduledAt = Instant.parse("2026-11-01T12:00:00Z");
        List<BookingEntity> bookings = List.of(
            booking(javaOfferingId, customerId, BookingStatus.CREATED, scheduledAt),
            booking(angularOfferingId, customerId, BookingStatus.CANCELLED, scheduledAt.plusSeconds(3600)),
            booking(cloudOfferingId, customerId, BookingStatus.CONFIRMED, scheduledAt.plusSeconds(7200))
        );
        bookingRepository.saveAll(bookings);
        bookingRepository.flush();

        var firstActivePage = bookingRepository.findListingByCustomerId(
            customerId, "ACTIVE", "TITLE_ASC", PageRequest.of(0, 1));
        var secondActivePage = bookingRepository.findListingByCustomerId(
            customerId, "ACTIVE", "TITLE_ASC", PageRequest.of(1, 1));
        var inactivePage = bookingRepository.findListingByCustomerId(
            customerId, "INACTIVE", "DATE_DESC", PageRequest.of(0, 10));
        var cheapestFirstPage = bookingRepository.findListingByCustomerId(
            customerId, "ALL", "PRICE_ASC", PageRequest.of(0, 3));

        assertThat(bookingRepository.findById(bookings.get(0).getId())).isPresent();
        assertThat(firstActivePage.getTotalElements()).isEqualTo(2);
        assertThat(firstActivePage.getTotalPages()).isEqualTo(2);
        assertThat(firstActivePage.getContent().get(0).getOfferingTitle()).isEqualTo("Diseño de Arquitectura Cloud");
        assertThat(secondActivePage.getContent().get(0).getOfferingTitle()).isEqualTo("Mentoría Java Backend");
        assertThat(inactivePage.getTotalElements()).isEqualTo(1);
        assertThat(inactivePage.getContent().get(0).getStatus()).isEqualTo("CANCELLED");
        assertThat(cheapestFirstPage.getContent())
            .extracting("price")
            .containsExactly(new java.math.BigDecimal("75000.00"), new java.math.BigDecimal("85000.00"), new java.math.BigDecimal("120000.00"));
        }

        private BookingEntity booking(UUID offeringId, UUID customerId, BookingStatus status, Instant scheduledAt) {
        return new BookingEntity(UUID.randomUUID(), offeringId, customerId, scheduledAt, status, Instant.now());
        }
}
