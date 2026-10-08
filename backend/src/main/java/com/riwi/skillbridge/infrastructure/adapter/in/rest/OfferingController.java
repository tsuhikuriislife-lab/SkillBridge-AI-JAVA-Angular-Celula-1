package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.offering.CatalogOfferingUseCase;
import com.riwi.skillbridge.application.port.in.offering.RetrieveOfferingUseCase;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingOut;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Catálogo público de servicios: solo ACTIVE con cupo disponible. */
@RestController
@RequestMapping("/api/services")
public class OfferingController {

    private final CatalogOfferingUseCase catalog;
    private final RetrieveOfferingUseCase retrieve;

    public OfferingController(CatalogOfferingUseCase catalog, RetrieveOfferingUseCase retrieve) {
        this.catalog = catalog;
        this.retrieve = retrieve;
    }

    @GetMapping
    public PageResult<OfferingOut> catalog(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(defaultValue = "name,asc") String sort) {
        return toOutPage(catalog.getActiveCatalog(
                PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size), sort));
    }

    @GetMapping("/search")
    public PageResult<OfferingOut> search(@RequestParam(required = false) String name,
                                          @RequestParam(required = false) UUID category,
                                          @RequestParam(required = false) Boolean free,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(defaultValue = "name,asc") String sort) {
        return toOutPage(catalog.searchCatalog(name, category, free,
                PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size), sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferingOut> getById(@PathVariable UUID id) {
        return retrieve.getOfferingById(id).map(o -> ResponseEntity.ok(toOut(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    private PageResult<OfferingOut> toOutPage(PageResult<Offering> p) {
        return new PageResult<>(p.content().stream().map(this::toOut).toList(),
                p.totalPages(), p.totalElements(), p.number());
    }

    private OfferingOut toOut(Offering o) {
        return new OfferingOut(o.id(), o.name(), o.categoryId(), o.price(), o.detail(),
                o.shortDescription(), o.learningObjectives(), o.prerequisites(),
                o.capacity(), o.code(), o.status(), o.createdBy(), o.createdAt(), o.updatedAt());
    }
}
