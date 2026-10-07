package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OfferingController {
    private final ListOfferingsUseCase useCase;
    private final OfferingRepositoryPort repositoryPort;

    public OfferingController(ListOfferingsUseCase useCase, OfferingRepositoryPort repositoryPort) { 
        this.useCase = useCase; 
        this.repositoryPort = repositoryPort;
    }

    @GetMapping("/offerings")
    public List<Offering> list() { return useCase.listActive(); }

    @GetMapping("/offerings/{id}")
    public Offering getById(@PathVariable UUID id) {
        return repositoryPort.findById(id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @GetMapping("/categories")
    public List<String> listCategories() {
        return repositoryPort.findDistinctCategories();
    }

    @GetMapping("/categories/top")
    public List<com.riwi.skillbridge.domain.model.CategoryCount> listTopCategories(@RequestParam(defaultValue = "8") int limit) {
        return repositoryPort.findTopCategories(limit);
    }
}
