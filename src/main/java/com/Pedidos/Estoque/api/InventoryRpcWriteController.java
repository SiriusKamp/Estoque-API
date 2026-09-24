package com.Pedidos.Estoque.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/stocks/{stockId}")
public class InventoryRpcWriteController {
    private final InventoryRpcWriteService service;

    public InventoryRpcWriteController(InventoryRpcWriteService service) {
        this.service = service;
    }

    /** Monolith: Products.tsx and Kits.tsx save_product RPC. */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode createProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody ProductSaveRequest request) {
        return service.saveProduct(actor(jwt), stockId, null, request.data(),
            request.competitorPrices(), request.kitProducts());
    }

    @PutMapping("/products/{productId}")
    public JsonNode updateProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @PathVariable UUID productId, @RequestBody ProductSaveRequest request) {
        return service.saveProduct(actor(jwt), stockId, productId, request.data(),
            request.competitorPrices(), request.kitProducts());
    }

    /** Monolith: Lots.tsx receive_lots RPC. */
    @PostMapping("/receipts")
    public OperationResult receiveLots(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody ReferencedOperation request) {
        UUID id = service.receiveLots(actor(jwt), stockId, request.reference(), request.data());
        return new OperationResult(id, request.reference().trim());
    }

    /** Monolith: Kits.tsx#ProductionEditor produce_kit RPC. */
    @PostMapping("/productions")
    public OperationResult produceKit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody ReferencedOperation request) {
        UUID id = service.produceKit(actor(jwt), stockId, request.reference(), request.data());
        return new OperationResult(id, request.reference().trim());
    }

    /** Monolith: InventoryImport.tsx import_inventory_batch RPC. */
    @PostMapping("/imports/{kind}")
    public JsonNode importBatch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @PathVariable String kind, @RequestBody ImportRequest request) {
        return service.importBatch(actor(jwt), stockId, kind, request.rows(), request.updateExisting());
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }

    public record ProductSaveRequest(JsonNode data, JsonNode competitorPrices, JsonNode kitProducts) { }
    public record ReferencedOperation(String reference, JsonNode data) { }
    public record ImportRequest(JsonNode rows, boolean updateExisting) { }
    public record OperationResult(UUID operationId, String reference) { }
}
