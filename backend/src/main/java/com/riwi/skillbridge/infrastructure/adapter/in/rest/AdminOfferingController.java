package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AdminManageOfferingsUseCase;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/offerings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOfferingController {
    private final AdminManageOfferingsUseCase manageOfferingsUseCase;

    public AdminOfferingController(AdminManageOfferingsUseCase manageOfferingsUseCase) {
        this.manageOfferingsUseCase = manageOfferingsUseCase;
    }

    @GetMapping
    public ResponseEntity<List<Offering>> listAll() {
        return ResponseEntity.ok(manageOfferingsUseCase.listAllOfferings());
    }

    @PostMapping
    public ResponseEntity<Offering> create(@RequestBody Map<String, Object> request) {
        String title = (String) request.get("title");
        String description = (String) request.get("description");
        String category = (String) request.get("category");
        BigDecimal price = request.get("price") != null ? new BigDecimal(request.get("price").toString()) : BigDecimal.ZERO;

        Offering created = manageOfferingsUseCase.createOffering(title, description, category, price);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Offering> update(@PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        String title = (String) updates.get("title");
        String description = (String) updates.get("description");
        String category = (String) updates.get("category");
        BigDecimal price = updates.get("price") != null ? new BigDecimal(updates.get("price").toString()) : null;

        Offering updated = manageOfferingsUseCase.updateOffering(id, title, description, category, price);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Offering> toggleStatus(@PathVariable UUID id) {
        Offering toggled = manageOfferingsUseCase.toggleStatus(id);
        return ResponseEntity.ok(toggled);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        manageOfferingsUseCase.deleteOffering(id);
        return ResponseEntity.noContent().build();
    }
}
