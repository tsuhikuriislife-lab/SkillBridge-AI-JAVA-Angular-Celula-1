package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.OfferingPage;
import com.riwi.skillbridge.application.model.OfferingSort;
import com.riwi.skillbridge.application.port.in.ManageProviderOfferingsUseCase;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.OfferingStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/provider/offerings")
public class ProviderOfferingController {
    private final ManageProviderOfferingsUseCase useCase;

    public ProviderOfferingController(ManageProviderOfferingsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Offering create(@Valid @RequestBody OfferingRequest request, Authentication authentication) {
        return useCase.create(request.toCommand(), authentication.getName());
    }

    @PutMapping("/{id}")
    public Offering update(@PathVariable UUID id,
                           @Valid @RequestBody OfferingRequest request,
                           Authentication authentication) {
        return useCase.update(id, request.toCommand(), authentication.getName());
    }

    @PatchMapping("/{id}/status")
    public Offering changeStatus(@PathVariable UUID id,
                                 @Valid @RequestBody OfferingStatusRequest request,
                                 Authentication authentication) {
        return useCase.changeStatus(id, request.status(), authentication.getName());
    }

    @GetMapping
    public OfferingPage listMine(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(defaultValue = "NAME_ASC") OfferingSort sort,
                                 Authentication authentication) {
        return useCase.listMine(authentication.getName(), page, size, sort);
    }
}