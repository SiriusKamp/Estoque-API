package com.Pedidos.Estoque.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CatalogWriteController {
    private final CatalogWriteService service;

    public CatalogWriteController(CatalogWriteService service) {
        this.service = service;
    }

    @PostMapping("/stocks")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createStock(@AuthenticationPrincipal Jwt jwt, @RequestBody NameRequest request) {
        return service.createStock(actor(jwt), request.name());
    }

    @PutMapping("/stocks/{stockId}")
    public Map<String, Object> renameStock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody NameRequest request) {
        return service.renameStock(actor(jwt), stockId, request.name());
    }

    @DeleteMapping("/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId) {
        service.deleteStock(actor(jwt), stockId);
    }

    @PostMapping("/stocks/{stockId}/product-types")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody NameRequest request) {
        return service.createType(actor(jwt), stockId, request.name());
    }

    @PutMapping("/stocks/{stockId}/product-types/{typeId}")
    public Map<String, Object> updateType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @PathVariable UUID typeId, @RequestBody NameRequest request) {
        return service.updateType(actor(jwt), stockId, typeId, request.name());
    }

    @DeleteMapping("/stocks/{stockId}/product-types/{typeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @PathVariable UUID typeId) {
        service.deleteType(actor(jwt), stockId, typeId);
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }

    public record NameRequest(String name) { }
}
