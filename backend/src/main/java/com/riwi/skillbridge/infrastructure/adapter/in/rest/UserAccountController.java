package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.useraccount.ChangeStatusUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.DeleteUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.RetrieveUserAccountUseCase;
import com.riwi.skillbridge.application.port.in.useraccount.UpdateUserAccountUseCase;
import com.riwi.skillbridge.domain.model.PageResult;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.ChangeStatusRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UpdateUserAccountRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UserAccountOut;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserAccountController {

    private final RetrieveUserAccountUseCase retrieveUseCase;
    private final UpdateUserAccountUseCase updateUseCase;
    private final ChangeStatusUserAccountUseCase changeStatusUseCase;
    private final DeleteUserAccountUseCase deleteUseCase;

    public UserAccountController(RetrieveUserAccountUseCase retrieveUseCase,
                                 UpdateUserAccountUseCase updateUseCase,
                                 ChangeStatusUserAccountUseCase changeStatusUseCase,
                                 DeleteUserAccountUseCase deleteUseCase) {
        this.retrieveUseCase = retrieveUseCase;
        this.updateUseCase = updateUseCase;
        this.changeStatusUseCase = changeStatusUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    // GET /users/{id} — ver un usuario
    @GetMapping("/{id}")
    public ResponseEntity<UserAccountOut> getById(@PathVariable UUID id) {
        return retrieveUseCase.getUserAccountById(id)
            .map(u -> ResponseEntity.ok(toOut(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    // GET /users — ver todos (alfabético)
    @GetMapping
    public PageResult<UserAccountOut> getAll(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        var result = retrieveUseCase.getAllUserAccounts(
                PageRequestUtils.clampPage(page), PageRequestUtils.clampSize(size));
        return new PageResult<>(result.content().stream().map(this::toOut).toList(),
                result.totalPages(), result.totalElements(), result.number());
    }

    @GetMapping("/all")
    public List<UserAccountOut> getAllList() {
        return retrieveUseCase.getAllSortedByName().stream().map(this::toOut).toList();
    }

    // GET /users/search?name=lu&email=gmail — filtrar por nombre y/o email
    @GetMapping("/search")
    public List<UserAccountOut> search(@RequestParam(required = false) String name,
                                       @RequestParam(required = false) String email) {
        if (name != null && !name.isBlank()) {
            return retrieveUseCase.searchByName(name).stream().map(this::toOut).toList();
        }
        if (email != null && !email.isBlank()) {
            return retrieveUseCase.searchByEmail(email).stream().map(this::toOut).toList();
        }
        // ✅ lista completa ordenada (sin paginar) cuando no hay filtros
        return retrieveUseCase.getAllSortedByName().stream().map(this::toOut).toList();
    }

    // GET /users/service/{serviceId} — SOLO los usuarios de ese servicio
    @GetMapping("/service/{serviceId}")
    public List<UserAccountOut> getByService(@PathVariable UUID serviceId) {
        return retrieveUseCase.getUsersByService(serviceId).stream().map(this::toOut).toList();
    }

    // PUT /users/{id} — editar perfil (solo name, image, gender, birthDate)
    @PutMapping("/{id}")
    public ResponseEntity<UserAccountOut> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateUserAccountRequest request) {
        return updateUseCase.updateUserAccount(id, request.name(), request.image(),
                request.gender(), request.birthDate())
            .map(u -> ResponseEntity.ok(toOut(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    // PATCH /users/{id}/status — activar/desactivar
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserAccountOut> changeStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody ChangeStatusRequest request) {
        return changeStatusUseCase.changeStatus(id, request.active())
            .map(u -> ResponseEntity.ok(toOut(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /users/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return deleteUseCase.deleteUserAccount(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    private UserAccountOut toOut(UserAccount u) {
        return new UserAccountOut(
            u.id(), u.name(), u.email(), u.role(), u.image(),
            u.gender(), u.birthDate(), u.status(), u.createdAt(), u.updatedAt());
    }
}
