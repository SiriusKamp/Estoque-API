package com.Pedidos.Estoque.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read endpoints replacing the monolith's direct Supabase table/view requests. */
@RestController
@RequestMapping("/api/v1")
public class InventoryReadController {
    private final InventoryReadService service;

    public InventoryReadController(InventoryReadService service) {
        this.service = service;
    }

    /** Monolith: src/hooks/useStocks.tsx#StockProvider query. */
    @GetMapping("/stocks")
    public List<Map<String, Object>> stocks(@AuthenticationPrincipal Jwt jwt) {
        return service.stocks(actor(jwt));
    }

    /** Monolith: src/hooks/useProducts.ts#useInventoryRows('product_types'). */
    @GetMapping("/stocks/{stockId}/product-types")
    public ApiPage<Map<String, Object>> productTypes(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("stockId") UUID stockId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "search", defaultValue = "") String search) {
        return service.productTypes(actor(jwt), stockId, page, size, search);
    }

    /** Monolith: src/hooks/useProducts.ts#useProducts, including the kit picker. */
    @GetMapping("/stocks/{stockId}/products")
    public ApiPage<Map<String, Object>> products(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("stockId") UUID stockId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "kind", defaultValue = "all") String kind,
            @RequestParam(name = "active", defaultValue = "all") String active,
            @RequestParam(name = "availability", defaultValue = "all") String availability,
            @RequestParam(name = "typeId", required = false) UUID typeId) {
        return service.products(actor(jwt), stockId, page, size, search,
            kind, active, availability, typeId);
    }

    /** Monolith: src/hooks/useProducts.ts#useSuppliers. */
    @GetMapping("/stocks/{stockId}/suppliers")
    public ApiPage<Map<String, Object>> suppliers(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("stockId") UUID stockId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "search", defaultValue = "") String search) {
        return service.suppliers(actor(jwt), stockId, page, size, search);
    }

    /** Monolith: Products.tsx#ProductEditor competitor_prices query. */
    @GetMapping("/stocks/{stockId}/products/{productId}/competitor-prices")
    public ApiPage<Map<String, Object>> competitorPrices(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @PathVariable UUID productId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size) {
        return service.competitorPrices(actor(jwt), stockId, productId, page, size);
    }

    /** Monolith: src/hooks/useProducts.ts#useInventoryRows('lot_details'). */
    @GetMapping("/stocks/{stockId}/lots")
    public ApiPage<Map<String, Object>> lots(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("stockId") UUID stockId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "balance", defaultValue = "all") String balance,
            @RequestParam(name = "expiry", defaultValue = "all") String expiry,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return service.lots(actor(jwt), stockId, page, size, search, productId,
            balance, expiry, instant(from), instant(to));
    }

    /** Monolith: src/hooks/useProducts.ts#useInventoryRows('movement_details'). */
    @GetMapping("/stocks/{stockId}/movements")
    public ApiPage<Map<String, Object>> movements(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("stockId") UUID stockId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "30") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "kind", defaultValue = "") String kind,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return service.movements(actor(jwt), stockId, page, size, search,
            productId, kind, instant(from), instant(to));
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalidDate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Use datas ISO-8601 com fuso horário em from/to.");
        }
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }
}
