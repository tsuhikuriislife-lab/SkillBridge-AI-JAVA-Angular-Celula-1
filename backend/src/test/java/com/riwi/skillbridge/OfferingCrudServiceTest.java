package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.CatalogItemPort;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.application.service.OfferingCrudService;
import com.riwi.skillbridge.domain.enums.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferingCrudServiceTest {

    @Mock OfferingPort offeringPort;
    @Mock UserAccountPort userAccountPort;
    @Mock CatalogItemPort catalogItemPort;
    @InjectMocks OfferingCrudService service;

    private final UUID categoryId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    private Offering valid() {
        return new Offering(null, "Curso Java", categoryId, new BigDecimal("50.00"),
                null, "Desc", null, null, 20, "JAVA-01", null, providerId, null, null);
    }

    private void stubOk() {
        when(catalogItemPort.findById(categoryId)).thenReturn(Optional.of(
                new CatalogItem(categoryId, "Backend", "CAT-BACK", null, CatalogType.CATEGORY, Status.ACTIVE)));
        when(userAccountPort.findById(providerId)).thenReturn(Optional.of(
                new UserAccount(providerId, "P", "p@x.com", Role.PROVIDER, null, "hash",
                        null, null, Status.ACTIVE, null, null)));
        when(offeringPort.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void create_ok_defaultsToDraft() {
        stubOk();
        var created = service.createOffering(valid());
        assertEquals(ServiceStatus.DRAFT, created.status());
        assertNotNull(created.createdAt());
    }

    @Test
    void create_rejectsNegativePrice() {
        var o = new Offering(null, "X", categoryId, new BigDecimal("-1"), null, null, null,
                null, 20, "C1", null, providerId, null, null);
        assertThrows(BusinessRuleException.class, () -> service.createOffering(o));
    }

    @Test
    void create_rejectsZeroCapacity() {
        var o = new Offering(null, "X", categoryId, BigDecimal.ZERO, null, null, null,
                null, 0, "C1", null, providerId, null, null);
        assertThrows(BusinessRuleException.class, () -> service.createOffering(o));
    }

    @Test
    void create_rejectsInactiveCategory() {
        when(catalogItemPort.findById(categoryId)).thenReturn(Optional.of(
                new CatalogItem(categoryId, "Backend", "CAT", null, CatalogType.CATEGORY, Status.INACTIVE)));
        assertThrows(BusinessRuleException.class, () -> service.createOffering(valid()));
    }

    @Test
    void create_rejectsNonProviderCreator() {
        when(catalogItemPort.findById(categoryId)).thenReturn(Optional.of(
                new CatalogItem(categoryId, "Backend", "CAT", null, CatalogType.CATEGORY, Status.ACTIVE)));
        when(userAccountPort.findById(providerId)).thenReturn(Optional.of(
                new UserAccount(providerId, "C", "c@x.com", Role.CUSTOMER, null, "h",
                        null, null, Status.ACTIVE, null, null)));
        assertThrows(BusinessRuleException.class, () -> service.createOffering(valid()));
    }

    @Test
    void create_rejectsDuplicateCode() {
        stubOk();
        when(offeringPort.findByCode("JAVA-01")).thenReturn(Optional.of(
                new Offering(UUID.randomUUID(), "Otro", categoryId, BigDecimal.ONE, null, null,
                        null, null, 5, "JAVA-01", ServiceStatus.ACTIVE, providerId, null, null)));
        assertThrows(BusinessRuleException.class, () -> service.createOffering(valid()));
    }

    @Test
    void delete_deactivatesInsteadOfRemoving() {
        var id = UUID.randomUUID();
        var existing = new Offering(id, "S", categoryId, BigDecimal.ZERO, null, null, null,
                null, 10, "C9", ServiceStatus.ACTIVE, providerId, null, null);
        when(offeringPort.findById(id)).thenReturn(Optional.of(existing));
        when(offeringPort.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.deleteOffering(id));
        verify(offeringPort).save(argThat(o -> o.status() == ServiceStatus.INACTIVE));
        verify(offeringPort, never()).deleteById(any());
    }
}
