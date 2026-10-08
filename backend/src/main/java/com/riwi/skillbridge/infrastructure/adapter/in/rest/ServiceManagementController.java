package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.offering.*;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Gestión de servicios del proveedor: solo puede tocar LOS SUYOS. */
@RestController
@RequestMapping("/api/provider/services")
@PreAuthorize("hasRole('PROVIDER')")
public class ServiceManagementController {

    private final CreateOfferingUseCase create;
    private final RetrieveOfferingUseCase retrieve;
    private final UpdateOfferingUseCase update;
    private final DeleteOfferingUseCase delete;
    private final UserAccountPort userAccountPort;

    public ServiceManagementController(CreateOfferingUseCase create, RetrieveOfferingUseCase retrieve,
                                       UpdateOfferingUseCase update, DeleteOfferingUseCase delete,
                                       UserAccountPort userAccountPort) {
        this.create = create; this.retrieve = retrieve; this.update = update;
        this.delete = delete; this.userAccountPort = userAccountPort;
    }

    @PostMapping
    public OfferingOut create(@AuthenticationPrincipal UserDetails user,
                              @Valid @RequestBody ServiceUpsertRequest req) {
        Offering created = create.createOffering(new Offering(null, req.name(), req.categoryId(),
                req.price(), req.detail(), req.shortDescription(), req.learningObjectives(),
                req.prerequisites(), req.capacity(), req.code(), null, currentUserId(user), null, null));
        return toOut(created);
    }

    @GetMapping("/me")
    public List<OfferingOut> myServices(@AuthenticationPrincipal UserDetails user) {
        return retrieve.getOfferingsByCreator(currentUserId(user)).stream()
                .map(this::toOut).toList();
    }

    @PutMapping("/{id}")
    public ResponseEntity<OfferingOut> update(@AuthenticationPrincipal UserDetails user,
                                              @PathVariable UUID id,
                                              @Valid @RequestBody ServiceUpsertRequest req) {
        assertOwnership(user, id);
        return update.updateOffering(id, new Offering(null, req.name(), req.categoryId(), req.price(),
                        req.detail(), req.shortDescription(), req.learningObjectives(),
                        req.prerequisites(), req.capacity(), req.code(), null, null, null, null))
                .map(o -> ResponseEntity.ok(toOut(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OfferingOut> changeStatus(@AuthenticationPrincipal UserDetails user,
                                                    @PathVariable UUID id,
                                                    @Valid @RequestBody ServiceStatusRequest req) {
        assertOwnership(user, id);
        return update.updateOffering(id, new Offering(null, null, null, null, null, null, null, null,
                        null, null, req.status(), null, null, null))
                .map(o -> ResponseEntity.ok(toOut(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Desactiva el servicio (eliminación lógica, la fila se conserva). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserDetails user,
                                           @PathVariable UUID id) {
        assertOwnership(user, id);
        delete.deleteOffering(id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(UserDetails user) {
        return userAccountPort.findByEmail(user.getUsername())
                .orElseThrow(() -> new BusinessRuleException("Usuario no encontrado")).id();
    }

    private void assertOwnership(UserDetails user, UUID serviceId) {
        UUID providerId = currentUserId(user);
        Offering existing = retrieve.getOfferingById(serviceId)
                .orElseThrow(() -> new BusinessRuleException("Servicio no encontrado"));
        if (!existing.createdBy().equals(providerId))
            throw new BusinessRuleException("No puedes modificar un servicio que no te pertenece");
    }

    private OfferingOut toOut(Offering o) {
        return new OfferingOut(o.id(), o.name(), o.categoryId(), o.price(), o.detail(),
                o.shortDescription(), o.learningObjectives(), o.prerequisites(),
                o.capacity(), o.code(), o.status(), o.createdBy(), o.createdAt(), o.updatedAt());
    }
}
