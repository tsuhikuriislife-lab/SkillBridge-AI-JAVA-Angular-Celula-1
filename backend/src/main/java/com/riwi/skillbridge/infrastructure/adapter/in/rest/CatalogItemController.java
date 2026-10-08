package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.catalog.DeleteCatalogItemUseCase;
import com.riwi.skillbridge.application.port.in.catalog.RetrieveCatalogItemUseCase;
import com.riwi.skillbridge.application.port.in.catalog.UpdateCatalogItemUseCase;
import com.riwi.skillbridge.domain.enums.CatalogType;
import com.riwi.skillbridge.domain.model.CatalogItem;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CatalogItemOut;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.UpdateCatalogItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/catalog")
public class CatalogItemController {

    private final RetrieveCatalogItemUseCase retrieveUseCase;
    private final UpdateCatalogItemUseCase updateUseCase;
    private final DeleteCatalogItemUseCase deleteUseCase;
    // 🚫 OJO: NO inyecto CreateCatalogItemUseCase a propósito (REGLA 2)

    public CatalogItemController(RetrieveCatalogItemUseCase retrieveUseCase,
                                 UpdateCatalogItemUseCase updateUseCase,
                                 DeleteCatalogItemUseCase deleteUseCase) {
        this.retrieveUseCase = retrieveUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    // GET /catalog/{id}
    @GetMapping("/{id}")
    public ResponseEntity<CatalogItemOut> getById(@PathVariable UUID id) {
        return retrieveUseCase.getCatalogItemById(id)
            .map(i -> ResponseEntity.ok(toOut(i)))
            .orElse(ResponseEntity.notFound().build());
    }

    // GET /catalog/code/{code}
    @GetMapping("/code/{code}")
    public ResponseEntity<CatalogItemOut> getByCode(@PathVariable String code) {
        return retrieveUseCase.getCatalogItemByCode(code)
            .map(i -> ResponseEntity.ok(toOut(i)))
            .orElse(ResponseEntity.notFound().build());
    }

    // GET /catalog?type=PREFERENCE  o  ?type=CATEGORY  (alfabético)
    @GetMapping
    public List<CatalogItemOut> getByType(@RequestParam CatalogType type) {
        return retrieveUseCase.getAllByType(type).stream().map(this::toOut).toList();
    }

    // GET /catalog/search?name=java
    @GetMapping("/search")
    public List<CatalogItemOut> search(@RequestParam String name) {
        return retrieveUseCase.searchByName(name).stream().map(this::toOut).toList();
    }

    // PUT /catalog/{id} — editar name, detail, status
    @PutMapping("/{id}")
    public ResponseEntity<CatalogItemOut> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateCatalogItemRequest request) {
        CatalogItem changes = new CatalogItem(null, request.name(), null, request.detail(), null, request.status());
        return updateUseCase.updateCatalogItem(id, changes)
            .map(i -> ResponseEntity.ok(toOut(i)))
            .orElse(ResponseEntity.notFound().build());
    }

    // DELETE /catalog/{id} — 🛡️ NO borra: desactiva (REGLA 3)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        deleteUseCase.deleteCatalogItem(id);
        return ResponseEntity.noContent().build();
    }

    private CatalogItemOut toOut(CatalogItem i) {
        return new CatalogItemOut(i.id(), i.name(), i.code(), i.detail(), i.type(), i.status());
    }
}
