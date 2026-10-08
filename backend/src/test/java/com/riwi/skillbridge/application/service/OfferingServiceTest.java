package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OfferingServiceTest {
    @Test
    void shouldReturnCacheWithoutQueryingDatabase() {
        OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
        OfferingCachePort cache = mock(OfferingCachePort.class);
        var expected = List.of(buildOffering(UUID.randomUUID(), "Java", OfferingStatus.ACTIVE));
        when(cache.getActiveOfferings()).thenReturn(Optional.of(expected));

        var service = new OfferingService(repository, cache);
        var result = service.listActive();

        assertEquals(expected, result);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldLoadAndCacheOfferingsWhenCacheMisses() {
        OfferingRepositoryPort repository = mock(OfferingRepositoryPort.class);
        OfferingCachePort cache = mock(OfferingCachePort.class);
        var expected = List.of(buildOffering(UUID.randomUUID(), "Angular", OfferingStatus.ACTIVE));
        when(cache.getActiveOfferings()).thenReturn(Optional.empty());
        when(repository.findAllActive()).thenReturn(expected);

        var service = new OfferingService(repository, cache);

        assertEquals(expected, service.listActive());
        verify(repository).findAllActive();
        verify(cache).putActiveOfferings(expected);
    }

    private Offering buildOffering(UUID id, String name, OfferingStatus status) {
        return new Offering(id, "SRV-TEST", name, UUID.randomUUID(), BigDecimal.TEN,
                "Mentoría", null, null, null, null, status, UUID.randomUUID());
    }
}