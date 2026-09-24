package com.Pedidos.Estoque.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP contract for the monolith's decrement_inventory RPC (Movements.tsx). */
public record InventoryDecrementRequest(String reference, String reason, Instant occurredAt,
        List<Item> items) {
    public record Item(UUID productId, BigDecimal quantity, String unit) { }
}
