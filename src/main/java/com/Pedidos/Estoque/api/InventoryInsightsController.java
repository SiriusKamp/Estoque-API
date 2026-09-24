package com.Pedidos.Estoque.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/stocks/{stockId}")
public class InventoryInsightsController {
    private final InventoryInsightsService service;

    public InventoryInsightsController(InventoryInsightsService service) {
        this.service = service;
    }

    /** Monolith: Dashboard.tsx#inventory_dashboard. */
    @GetMapping("/dashboard")
    public JsonNode dashboard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return service.dashboard(actor(jwt), stockId, days);
    }

    /** Monolith: useProducts.ts#useRecipeEstimate. */
    @PostMapping("/recipe-estimates")
    public JsonNode estimateRecipe(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID stockId,
            @RequestBody RecipeEstimateRequest request) {
        return service.estimateRecipe(actor(jwt), stockId, request.items());
    }

    /** Monolith: useProducts.ts#useRecipe/#useKitContents. */
    @GetMapping("/kit-contents")
    public List<Map<String, Object>> kitContents(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestParam(name = "kitIds") List<UUID> kitIds) {
        return service.kitContents(actor(jwt), stockId, kitIds);
    }

    /** Monolith: useProducts.ts#useKitCapacities. */
    @GetMapping("/kit-capacities")
    public List<Map<String, Object>> kitCapacities(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestParam(name = "kitIds") List<UUID> kitIds) {
        return service.kitCapacities(actor(jwt), stockId, kitIds);
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }

    public record RecipeEstimateRequest(List<InventoryDecrementRequest.Item> items) { }
}
