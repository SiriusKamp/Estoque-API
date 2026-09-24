package com.Pedidos.Estoque.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/** Read contracts used by Dashboard.tsx and the kit hooks in useProducts.ts. */
@Service
@Transactional(readOnly = true)
public class InventoryInsightsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public InventoryInsightsService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Monolith: Dashboard.tsx inventory_dashboard(p_stock_id,p_days). */
    public JsonNode dashboard(UUID actorId, UUID stockId, int days) {
        if (days < 1 || days > 366) throw bad("Período inválido: use de 1 a 366 dias.");
        authorize(actorId, stockId);
        String value = jdbc.queryForObject(
            "SELECT public.inventory_dashboard(:stock,:days)::text",
            new MapSqlParameterSource("stock", stockId).addValue("days", days), String.class);
        return parse(value);
    }

    /** Monolith: useProducts.ts#useRecipeEstimate and the production preview. */
    public JsonNode estimateRecipe(UUID actorId, UUID stockId,
            List<InventoryDecrementRequest.Item> items) {
        List<Map<String, Object>> payload = InventoryDecrementService.validatedItems(items);
        HashSet<UUID> seen = new HashSet<>();
        for (InventoryDecrementRequest.Item item : items) {
            if (!seen.add(item.productId())) throw bad("Não repita componentes na estimativa.");
        }
        authorize(actorId, stockId);
        String value = jdbc.queryForObject(
            "SELECT public.estimate_recipe(:stock,CAST(:items AS jsonb))::text",
            new MapSqlParameterSource("stock", stockId).addValue("items", stringify(payload)),
            String.class);
        return parse(value);
    }

    /** Monolith: useProducts.ts#useRecipe and #useKitContents. */
    public List<Map<String, Object>> kitContents(UUID actorId, UUID stockId, List<UUID> kitIds) {
        List<UUID> ids = validatedIds(kitIds);
        authorize(actorId, stockId);
        return jdbc.query("SELECT kp.kit_id,kp.product_id,kp.quantity,kp.unit,"
                + "p.name,p.sku,p.content_quantity,p.content_unit,p.is_kit,"
                + "p.profit_rate,p.suggested_sale_price,p.active "
                + "FROM public.kit_products kp "
                + "JOIN public.products k ON k.id=kp.kit_id AND k.stock_id=:stock AND k.is_kit "
                + "JOIN public.products p ON p.id=kp.product_id AND p.stock_id=:stock "
                + "WHERE kp.kit_id IN (:ids) ORDER BY kp.kit_id,kp.id",
            new MapSqlParameterSource("stock", stockId).addValue("ids", ids), (rs, row) -> {
                Map<String, Object> product = new LinkedHashMap<>();
                product.put("id", rs.getObject("product_id", UUID.class).toString());
                product.put("name", rs.getString("name"));
                product.put("sku", rs.getString("sku"));
                product.put("content_quantity", rs.getBigDecimal("content_quantity"));
                product.put("content_unit", rs.getString("content_unit"));
                product.put("is_kit", rs.getBoolean("is_kit"));
                product.put("profit_rate", rs.getBigDecimal("profit_rate"));
                product.put("suggested_sale_price", rs.getBigDecimal("suggested_sale_price"));
                product.put("active", rs.getBoolean("active"));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("kit_id", rs.getObject("kit_id", UUID.class).toString());
                result.put("product_id", rs.getObject("product_id", UUID.class).toString());
                result.put("quantity", rs.getBigDecimal("quantity"));
                result.put("unit", rs.getString("unit"));
                result.put("products", product);
                return result;
            });
    }

    /** Monolith: useProducts.ts#useKitCapacities (kit_capacity view). */
    public List<Map<String, Object>> kitCapacities(UUID actorId, UUID stockId, List<UUID> kitIds) {
        List<UUID> ids = validatedIds(kitIds);
        authorize(actorId, stockId);
        return jdbc.query("WITH selected AS MATERIALIZED (SELECT id FROM public.products "
                + "WHERE stock_id=:stock AND is_kit AND id IN (:ids)) "
                + "SELECT p.id AS kit_id,c.whole_units,c.max_base_quantity,c.base_unit "
                + "FROM selected p CROSS JOIN LATERAL public.estimate_kit_capacity(:stock,p.id) c "
                + "ORDER BY p.id",
            new MapSqlParameterSource("stock", stockId).addValue("ids", ids), (rs, row) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("kit_id", rs.getObject("kit_id", UUID.class).toString());
                result.put("whole_units", rs.getBigDecimal("whole_units"));
                result.put("max_base_quantity", rs.getBigDecimal("max_base_quantity"));
                result.put("base_unit", rs.getString("base_unit"));
                return result;
            });
    }

    private void authorize(UUID actorId, UUID stockId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> owned = jdbc.query("SELECT id FROM public.stocks "
                + "WHERE id=:stock AND user_id=:actor",
            new MapSqlParameterSource("stock", stockId).addValue("actor", actorId),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (owned.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Estoque não encontrado.");
        // These read RPCs still call assert_stock()/auth.uid() inside PostgreSQL.
        UUID databaseActor = jdbc.queryForObject("SELECT auth.uid()",
            new MapSqlParameterSource(), UUID.class);
        if (!actorId.equals(databaseActor)) {
            throw new IllegalStateException("O contexto de identidade do banco não foi aplicado.");
        }
    }

    private static List<UUID> validatedIds(List<UUID> kitIds) {
        if (kitIds == null || kitIds.isEmpty() || kitIds.size() > 100 || kitIds.contains(null)) {
            throw bad("Informe de 1 a 100 kits.");
        }
        return new ArrayList<>(new HashSet<>(kitIds));
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (JacksonException invalidJson) {
            throw new IllegalStateException("Resposta inválida do banco de estoque.", invalidJson);
        }
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException invalidJson) {
            throw new IllegalStateException("Falha ao codificar estimativa validada.", invalidJson);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
