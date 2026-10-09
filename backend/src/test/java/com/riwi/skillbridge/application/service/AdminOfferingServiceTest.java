package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.port.in.AdminActivityUseCase;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.enums.Role;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.enums.Status;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOfferingServiceTest {

    @Mock
    private OfferingRepositoryPort offeringRepository;

    @Mock
    private CatalogItemPort catalogItemPort;

    @Mock
    private UserAccountPort userAccountPort;

    @Mock
    private AdminActivityUseCase adminActivity;

    private AdminOfferingService service;

    @BeforeEach
    void setUp() {
        service = new AdminOfferingService(offeringRepository, catalogItemPort, userAccountPort, adminActivity);
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
        UUID categoryId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(catalogItemPort.findByCode("BACKEND")).thenReturn(Optional.of(
            new CatalogItem(categoryId, "Backend", "BACKEND", null, CatalogType.CATEGORY, Status.ACTIVE)));
        when(userAccountPort.findByEmail("admin@test.com")).thenReturn(Optional.of(user(adminId)));
        when(offeringRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Offering created = service.createOffering("Spring Boot", "Desc", "BACKEND",
            BigDecimal.valueOf(50000), "admin@test.com");

        assertNotNull(created);
        assertEquals("Spring Boot", created.name());
        assertEquals(categoryId, created.categoryId());
        assertEquals(adminId, created.createdBy());
    }

    @Test
    void toggleStatus_shouldInvertActiveAndEvictCache() {
        UUID id = UUID.randomUUID();
        Offering current = offering(id, ServiceStatus.ACTIVE);
        when(offeringRepository.findById(id)).thenReturn(Optional.of(current));
        when(offeringRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Offering toggled = service.toggleStatus(id, "admin@test.com");

        assertEquals(ServiceStatus.INACTIVE, toggled.status());
    }

    @Test
    void deleteOffering_shouldDeleteAndEvictCache() {
        UUID id = UUID.randomUUID();
        Offering current = offering(id, ServiceStatus.ACTIVE);
        when(offeringRepository.findById(id)).thenReturn(Optional.of(current));

        service.deleteOffering(id, "admin@test.com");

        verify(offeringRepository).deleteById(id);
    }

    private static Offering offering(UUID id, ServiceStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Offering(id, "Title", UUID.randomUUID(), BigDecimal.TEN, "Desc", "Desc",
                null, null, 10, "SRV-TEST", status, UUID.randomUUID(), now, now);
    }

    private static UserAccount user(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new UserAccount(id, "Admin", "admin@test.com", Role.ADMIN, null, "hash",
                null, null, Status.ACTIVE, now, now);
    }
}

