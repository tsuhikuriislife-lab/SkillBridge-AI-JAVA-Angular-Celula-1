package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOfferingServiceTest {

    @Mock
    private OfferingRepositoryPort offeringRepository;

    @Mock
    private OfferingCachePort offeringCache;

    private AdminOfferingService service;

    @BeforeEach
    void setUp() {
        service = new AdminOfferingService(offeringRepository, offeringCache);
    }

    @Test
    void listAllOfferings_shouldReturnOfferings() {
        when(offeringRepository.findAll()).thenReturn(java.util.List.of());
        var result = service.listAllOfferings();
        assertNotNull(result);
        verify(offeringRepository).findAll();
    }

    @Test
    void createOffering_shouldSaveAndEvictCache() {
        when(offeringRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Offering created = service.createOffering("Spring Boot", "Desc", "BACKEND", BigDecimal.valueOf(50000));

        assertNotNull(created);
        assertEquals("Spring Boot", created.title());
        verify(offeringCache).evictActiveOfferings();
    }

    @Test
    void toggleStatus_shouldInvertActiveAndEvictCache() {
        UUID id = UUID.randomUUID();
        Offering current = new Offering(id, "Title", "Desc", "CAT", BigDecimal.TEN, true, null, null, null, null, null);
        when(offeringRepository.findById(id)).thenReturn(Optional.of(current));
        when(offeringRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Offering toggled = service.toggleStatus(id);

        assertFalse(toggled.active());
        verify(offeringCache).evictActiveOfferings();
    }

    @Test
    void deleteOffering_shouldDeleteAndEvictCache() {
        UUID id = UUID.randomUUID();
        Offering current = new Offering(id, "Title", "Desc", "CAT", BigDecimal.TEN, true, null, null, null, null, null);
        when(offeringRepository.findById(id)).thenReturn(Optional.of(current));

        service.deleteOffering(id);

        verify(offeringRepository).deleteById(id);
        verify(offeringCache).evictActiveOfferings();
    }
}

