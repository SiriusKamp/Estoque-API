package com.Pedidos.Estoque.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class InventoryWriteController {
    private final InventoryDecrementService decrements;
    private final StockApiClientService clients;

    public InventoryWriteController(InventoryDecrementService decrements, StockApiClientService clients) {
        this.decrements = decrements;
        this.clients = clients;
    }

    /** Monolith: Movements.tsx#DecrementEditor -> decrement_inventory RPC. */
    @PostMapping("/stocks/{stockId}/decrements")
    public DecrementResult userDecrement(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestBody InventoryDecrementRequest request) {
        UUID id = decrements.decrement(actor(jwt), stockId, request, null);
        return new DecrementResult(id, request.reference().trim());
    }

    /** Orders/comandas call this after completion, retrying the same stable reference on timeout. */
    @PostMapping("/integrations/stocks/{stockId}/decrements")
    public DecrementResult serviceDecrement(@PathVariable UUID stockId,
            @RequestHeader(name = "X-Stock-Key", required = false) String secret,
            @RequestBody InventoryDecrementRequest request) {
        StockApiClientService.ClientIdentity client = clients.authenticate(stockId, secret);
        UUID id = decrements.decrement(client.stockOwnerId(), stockId, request, client.id());
        return new DecrementResult(id, request.reference().trim());
    }

    @PostMapping("/stocks/{stockId}/api-clients")
    @ResponseStatus(HttpStatus.CREATED)
    public StockApiClientService.CreatedClient createClient(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestBody CreateClientRequest request) {
        return clients.create(actor(jwt), stockId, request.name());
    }

    @DeleteMapping("/stocks/{stockId}/api-clients/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeClient(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @PathVariable UUID clientId) {
        clients.revoke(actor(jwt), stockId, clientId);
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }

    public record CreateClientRequest(String name) { }
    public record DecrementResult(UUID operationId, String reference) { }
}
