package com.Pedidos.Estoque.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicit HTTP facade over the existing transactional stock RPCs. */
@Service
public class InventoryRpcWriteService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public InventoryRpcWriteService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Monolith: Products.tsx and Kits.tsx -> save_product. */
    @Transactional
    public JsonNode saveProduct(UUID actorId, UUID stockId, UUID productId, JsonNode data,
            JsonNode competitorPrices, JsonNode kitProducts) {
        if (data == null || !data.isObject()
                || (competitorPrices != null && !competitorPrices.isArray())
                || (kitProducts != null && !kitProducts.isArray())) {
            throw bad("Dados de produto ou receita inválidos.");
        }
        authorize(actorId, stockId);
        String result = jdbc.queryForObject(
            "SELECT to_jsonb(public.save_product(:stock,CAST(:productId AS uuid),"
                + "CAST(:data AS jsonb),CAST(:competitors AS jsonb),CAST(:components AS jsonb)))::text",
            new MapSqlParameterSource("stock", stockId).addValue("productId", productId)
                .addValue("data", data.toString()).addValue("competitors", jsonText(competitorPrices))
                .addValue("components", jsonText(kitProducts)), String.class);
        return parse(result);
    }

    /** Monolith: Lots.tsx -> receive_lots. */
    @Transactional
    public UUID receiveLots(UUID actorId, UUID stockId, String reference, JsonNode data) {
        validateOperation(reference, data);
        authorize(actorId, stockId);
        return jdbc.queryForObject("SELECT public.receive_lots(:stock,:reference,CAST(:data AS jsonb))",
            operationParams(stockId, reference, data), UUID.class);
    }

    /** Monolith: Kits.tsx#ProductionEditor -> produce_kit. */
    @Transactional
    public UUID produceKit(UUID actorId, UUID stockId, String reference, JsonNode data) {
        validateOperation(reference, data);
        authorize(actorId, stockId);
        return jdbc.queryForObject("SELECT public.produce_kit(:stock,:reference,CAST(:data AS jsonb))",
            operationParams(stockId, reference, data), UUID.class);
    }

    /** Monolith: InventoryImport.tsx -> import_inventory_batch. */
    @Transactional
    public JsonNode importBatch(UUID actorId, UUID stockId, String kind, JsonNode rows,
            boolean updateExisting) {
        if (!List.of("types", "products", "lots", "decrements").contains(kind)
                || rows == null || !rows.isArray() || rows.isEmpty() || rows.size() > 100) {
            throw bad("Lote de importação inválido. Envie de 1 a 100 linhas.");
        }
        authorize(actorId, stockId);
        String result = jdbc.queryForObject(
            "SELECT public.import_inventory_batch(:stock,:kind,CAST(:rows AS jsonb),:update)::text",
            new MapSqlParameterSource("stock", stockId).addValue("kind", kind)
                .addValue("rows", rows.toString()).addValue("update", updateExisting), String.class);
        return parse(result);
    }

    private void authorize(UUID actorId, UUID stockId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> owned = jdbc.query("SELECT id FROM public.stocks WHERE id=:stock AND user_id=:actor",
            new MapSqlParameterSource("stock", stockId).addValue("actor", actorId),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (owned.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Estoque não encontrado.");
        // The RPCs call assert_stock(), which relies on auth.uid().
        UUID databaseActor = DatabaseActorContext.currentActor(jdbc);
        if (!actorId.equals(databaseActor)) {
            throw new IllegalStateException("O contexto de identidade do banco não foi aplicado.");
        }
    }

    private static void validateOperation(String reference, JsonNode data) {
        if (reference == null || reference.isBlank() || reference.trim().length() > 200
                || data == null || !data.isObject()) {
            throw bad("Informe referência e dados válidos para a operação.");
        }
    }

    private static MapSqlParameterSource operationParams(UUID stockId, String reference, JsonNode data) {
        return new MapSqlParameterSource("stock", stockId).addValue("reference", reference.trim())
            .addValue("data", data.toString());
    }

    private static String jsonText(JsonNode value) {
        return value == null || value.isNull() ? null : value.toString();
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (JacksonException invalidJson) {
            throw new IllegalStateException("Resposta inválida do banco de estoque.", invalidJson);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
