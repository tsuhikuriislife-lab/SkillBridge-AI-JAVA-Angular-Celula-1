package com.riwi.skillbridge;

import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.application.service.OfferingCatalogService;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.enums.ServiceStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferingCatalogServiceTest {

    @Mock OfferingPort offeringPort;
    @Mock ServiceEnrollmentPort enrollmentPort;
    @InjectMocks OfferingCatalogService catalog;

    private Offering offering(String name, int capacity) {
        return new Offering(UUID.randomUUID(), name, UUID.randomUUID(), BigDecimal.TEN, null,
                null, null, null, capacity, name, ServiceStatus.ACTIVE, UUID.randomUUID(), null, null);
    }

    @Test
    void catalog_excludesFullServices() {
        var withRoom = offering("A", 10);
        var full = offering("B", 1);
        when(offeringPort.findCatalog(isNull(), isNull(), isNull(), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageResult<>(List.of(withRoom, full), 1, 2, 0));
        when(enrollmentPort.countByServiceIdAndStatus(eq(withRoom.id()), eq(EnrollmentStatus.ACTIVE)))
                .thenReturn(3L);
        when(enrollmentPort.countByServiceIdAndStatus(eq(full.id()), eq(EnrollmentStatus.ACTIVE)))
                .thenReturn(1L);

        var page = catalog.getActiveCatalog(0, 10, null);

        assertEquals(1, page.content().size());
        assertEquals("A", page.content().get(0).name());
    }

    @Test
    void catalog_returnsFewerThanSize_whenNotEnoughRecords() {
        when(offeringPort.findCatalog(isNull(), isNull(), isNull(), anyInt(), anyInt(), isNull()))
                .thenReturn(new PageResult<>(List.of(offering("Solo", 5)), 1, 1, 0));
        when(enrollmentPort.countByServiceIdAndStatus(any(UUID.class), eq(EnrollmentStatus.ACTIVE)))
                .thenReturn(0L);

        var page = catalog.getActiveCatalog(0, 50, null);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
    }
}
