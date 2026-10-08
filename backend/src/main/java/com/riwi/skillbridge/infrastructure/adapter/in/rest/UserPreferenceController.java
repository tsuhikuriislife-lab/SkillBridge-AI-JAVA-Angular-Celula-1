package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.model.UserPreferenceView;
import com.riwi.skillbridge.application.port.in.userpreference.ManageUserPreferenceUseCase;
import com.riwi.skillbridge.domain.model.UserPreference;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.AssignPreferenceRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UserPreferenceOut;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/{userId}/preferences")
public class UserPreferenceController {

    private final ManageUserPreferenceUseCase manageUseCase;

    public UserPreferenceController(ManageUserPreferenceUseCase manageUseCase) {
        this.manageUseCase = manageUseCase;
    }

    // GET /users/{userId}/preferences — las de su perfil (activas)
    @GetMapping
    public List<UserPreferenceOut> getByUser(@PathVariable UUID userId) {
        return manageUseCase.getPreferencesByUser(userId).stream()
            .map(this::toOut).toList();
    }

    // POST /users/{userId}/preferences — asignar (o reactivar)
    @PostMapping
    public ResponseEntity<UserPreferenceOut> assign(@PathVariable UUID userId,
                                                    @Valid @RequestBody AssignPreferenceRequest request) {
        UserPreference assigned = manageUseCase.assignPreference(
            new UserPreference(userId, request.preferenceId(), null));
        return ResponseEntity.ok(toOut(assigned));
    }

    // DELETE /users/{userId}/preferences/{preferenceId} — desactivar
    @DeleteMapping("/{preferenceId}")
    public ResponseEntity<Void> remove(@PathVariable UUID userId,
                                       @PathVariable UUID preferenceId) {
        manageUseCase.removePreference(userId, preferenceId);
        return ResponseEntity.noContent().build();
    }

    private UserPreferenceOut toOut(UserPreference p) {
        return new UserPreferenceOut(p.userId(), p.preferenceId(), p.status());
    }

    // GET /users/{userId}/preferences/catalog — TODAS las preferencias + cuáles tiene
    @GetMapping("/catalog")
    public List<UserPreferenceView> getCatalogWithAssignment(@PathVariable UUID userId) {
        return manageUseCase.getAllPreferencesWithAssignment(userId);
    }
}
