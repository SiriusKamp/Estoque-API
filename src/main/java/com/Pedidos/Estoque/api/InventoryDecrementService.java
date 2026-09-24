package com.Pedidos.Estoque.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryDecrementService {
    private static final Set<String> UNITS = Set.of("un", "ml", "l", "g", "kg");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public InventoryDecrementService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Monolith: src/pages/Movements.tsx#DecrementEditor and
     * src/hooks/useProducts.ts#useInventoryActions('decrement_inventory').
     * The PostgreSQL RPC remains the single FIFO/portion ledger writer during migration.
     */
    @Transactional
    public UUID decrement(UUID actorId, UUID stockId, InventoryDecrementRequest request,
            UUID integrationClientId) {
        Map<String, Object> payload = validatedPayload(request, integrationClientId);
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> stocks = jdbc.query(
            "SELECT id FROM public.stocks WHERE id = :stock AND user_id = :actor",
            new MapSqlParameterSource("stock", stockId).addValue("actor", actorId),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (stocks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estoque não encontrado.");
        }

        // Transaction-local Supabase claim: assert_stock() and the immutable ledger use auth.uid().
        // Never accept this actor from the HTTP body; it comes from a verified JWT or stock key.
        UUID databaseActor = jdbc.queryForObject("SELECT auth.uid()",
            new MapSqlParameterSource(), UUID.class);
        if (!actorId.equals(databaseActor)) {
            throw new IllegalStateException("O contexto de identidade do banco não foi aplicado.");
        }
        try {
            String body = json.writeValueAsString(payload);
            return jdbc.queryForObject("SELECT public.decrement_inventory(:stock, :reference, CAST(:data AS jsonb))",
                new MapSqlParameterSource("stock", stockId)
                    .addValue("reference", request.reference().trim())
                    .addValue("data", body), UUID.class);
        } catch (JacksonException invalidJson) {
            throw new IllegalStateException("Falha ao codificar baixa validada.", invalidJson);
        }
    }

    static Map<String, Object> validatedPayload(InventoryDecrementRequest request,
            UUID integrationClientId) {
        if (request == null || request.reference() == null || request.reference().isBlank()
                || request.reference().trim().length() > 200) {
            throw bad("Informe uma referência de até 200 caracteres.");
        }
        if (request.reason() == null || request.reason().isBlank()
                || request.reason().trim().length() > 500) {
            throw bad("Informe um motivo de até 500 caracteres.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", request.reason().trim());
        if (request.occurredAt() != null) payload.put("occurred_at", request.occurredAt().toString());
        payload.put("items", validatedItems(request.items()));
        if (integrationClientId != null) {
            // Keep idempotency stable if a stock owner rotates the integration key.
            payload.put("source", "stock-api-client");
        }
        return payload;
    }

    static List<Map<String, Object>> validatedItems(List<InventoryDecrementRequest.Item> requestItems) {
        if (requestItems == null || requestItems.isEmpty() || requestItems.size() > 100) {
            throw bad("Informe de 1 a 100 itens.");
        }
        List<Map<String, Object>> items = new ArrayList<>(requestItems.size());
        for (InventoryDecrementRequest.Item item : requestItems) {
            if (item == null || item.productId() == null || item.quantity() == null
                    || item.quantity().compareTo(BigDecimal.ZERO) <= 0
                    || item.quantity().compareTo(MAX_QUANTITY) > 0
                    || item.quantity().stripTrailingZeros().scale() > 6
                    || item.unit() == null || !UNITS.contains(item.unit())) {
                throw bad("Produto, quantidade ou unidade inválidos na baixa.");
            }
            items.add(Map.of("product_id", item.productId().toString(),
                "quantity", item.quantity(), "unit", item.unit()));
        }
        return items;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
