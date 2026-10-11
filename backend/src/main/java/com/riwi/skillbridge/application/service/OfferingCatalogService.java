package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.offering.CatalogOfferingUseCase;
import com.riwi.skillbridge.application.port.out.OfferingPort;
import com.riwi.skillbridge.application.port.out.ServiceEnrollmentPort;
import com.riwi.skillbridge.domain.enums.EnrollmentStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OfferingCatalogService implements CatalogOfferingUseCase {

    private final OfferingPort offeringPort;
    private final ServiceEnrollmentPort enrollmentPort;

    public OfferingCatalogService(OfferingPort offeringPort, ServiceEnrollmentPort enrollmentPort) {
        this.offeringPort = offeringPort;
        this.enrollmentPort = enrollmentPort;
    }

    @Override
    public PageResult<Offering> getActiveCatalog(int page, int size, String sort) {
        return searchCatalog(null, null, null, page, size, sort);
    }

    @Override
    public PageResult<Offering> searchCatalog(String name, UUID categoryId, Boolean freeOnly,
                                              int page, int size, String sort) {
        PageResult<Offering> result = offeringPort.findCatalog(name, categoryId, freeOnly, page, size, sort);
        // Solo servicios con cupos disponibles llegan al público
        List<Offering> available = result.content().stream()
                .filter(o -> o.capacity() == null || activeEnrollments(o.id()) < o.capacity())
                .toList();
        return new PageResult<>(available, result.totalPages(), result.totalElements(), result.number());
    }

    private long activeEnrollments(UUID serviceId) {
        return enrollmentPort.countByServiceIdAndStatus(serviceId, EnrollmentStatus.ACTIVE);
    }
}
