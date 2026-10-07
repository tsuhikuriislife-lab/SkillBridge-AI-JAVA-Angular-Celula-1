package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.ManageProviderOfferingsUseCase;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/provider/offerings")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderOfferingController {
    private final ManageProviderOfferingsUseCase useCase;

    public ProviderOfferingController(ManageProviderOfferingsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public Offering create(@AuthenticationPrincipal UserDetails user, @RequestBody Map<String, Object> request) {
        String email = user.getUsername();
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String category = (String) request.get("category");
        BigDecimal price = new BigDecimal(request.get("price").toString());
        LocalTime startTime = request.get("startTime") != null ? LocalTime.parse(request.get("startTime").toString()) : null;
        LocalTime endTime = request.get("endTime") != null ? LocalTime.parse(request.get("endTime").toString()) : null;
        String endDay = (String) request.get("endDay");
        String photoUrl = (String) request.get("photoUrl");

        return useCase.create(email, title, description, category, price, startTime, endTime, endDay, photoUrl);
    }

    @GetMapping("/me")
    public PageResult<Offering> getMyOfferings(@AuthenticationPrincipal UserDetails user,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return useCase.getProviderOfferings(user.getUsername(), page, size);
    }

    @PatchMapping("/{id}")
    public Offering update(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        return useCase.update(user.getUsername(), id, updates);
    }

    @PatchMapping("/{id}/status")
    public Offering toggleStatus(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return useCase.toggleStatus(user.getUsername(), id);
    }
}
