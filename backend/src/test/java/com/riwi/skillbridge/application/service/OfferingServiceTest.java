package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
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
        var expected = List.of(new Offering(UUID.randomUUID(), "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true, UUID.randomUUID(), java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0), "MONDAY,TUESDAY", "UTC"));
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
        var expected = List.of(new Offering(UUID.randomUUID(), "Angular", "Mentoría", "FRONTEND", BigDecimal.TEN, true));
        when(cache.getActiveOfferings()).thenReturn(Optional.empty());
        when(repository.findAllActive()).thenReturn(expected);

        var service = new OfferingService(repository, cache);

        assertEquals(expected, service.listActive());
        verify(repository).findAllActive();
        verify(cache).putActiveOfferings(expected);
    }
}
