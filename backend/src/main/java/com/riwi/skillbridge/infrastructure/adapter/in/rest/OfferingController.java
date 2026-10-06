package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ListOfferingsUseCase;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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

    @GetMapping("/categories")
    public List<String> listCategories() {
        return repositoryPort.findDistinctCategories();
    }
}
