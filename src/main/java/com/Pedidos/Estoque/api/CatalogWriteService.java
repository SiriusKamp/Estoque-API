package com.Pedidos.Estoque.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Stock/type writes replacing the monolith's direct Supabase table mutations. */
@Service
public class CatalogWriteService {
    private final NamedParameterJdbcTemplate jdbc;

    private static final RowMapper<Map<String, Object>> STOCK_ROW = (rs, row) -> {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getObject("id", UUID.class).toString());
        value.put("name", rs.getString("name"));
        value.put("user_id", rs.getObject("user_id", UUID.class).toString());
        value.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
        return value;
    };
    private static final RowMapper<Map<String, Object>> TYPE_ROW = (rs, row) -> {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", rs.getObject("id", UUID.class).toString());
        value.put("stock_id", rs.getObject("stock_id", UUID.class).toString());
        value.put("name", rs.getString("name"));
        value.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
        return value;
    };

    public CatalogWriteService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Monolith: useStocks.tsx#StockProvider#createStock. */
    @Transactional
    public Map<String, Object> createStock(UUID actorId, String name) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<Map<String, Object>> rows = jdbc.query(
            "INSERT INTO public.stocks(user_id,name) VALUES(:actor,:name) "
                + "RETURNING id,name,user_id,created_at",
            new MapSqlParameterSource("actor", actorId).addValue("name", cleanName(name, 100)),
            STOCK_ROW);
        return rows.getFirst();
    }

    /** Monolith: useStocks.tsx#StockProvider#renameStock. */
    @Transactional
    public Map<String, Object> renameStock(UUID actorId, UUID stockId, String name) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<Map<String, Object>> rows = jdbc.query(
            "UPDATE public.stocks SET name=:name WHERE id=:stock AND user_id=:actor "
                + "RETURNING id,name,user_id,created_at",
            new MapSqlParameterSource("actor", actorId).addValue("stock", stockId)
                .addValue("name", cleanName(name, 100)), STOCK_ROW);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    /** Monolith: useStocks.tsx#StockProvider#deleteStock. */
    @Transactional
    public void deleteStock(UUID actorId, UUID stockId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> deleted = jdbc.query(
            "DELETE FROM public.stocks WHERE id=:stock AND user_id=:actor RETURNING id",
            new MapSqlParameterSource("actor", actorId).addValue("stock", stockId),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (deleted.isEmpty()) throw missing();
    }

    /** Monolith: ProductTypes.tsx and useProductTypes.ts#addProductType. */
    @Transactional
    public Map<String, Object> createType(UUID actorId, UUID stockId, String name) {
        String validName = cleanName(name, 200);
        DatabaseActorContext.apply(jdbc, actorId);
        List<Map<String, Object>> rows = jdbc.query(
            "INSERT INTO public.product_types(stock_id,name) "
                + "SELECT id,:name FROM public.stocks WHERE id=:stock AND user_id=:actor "
                + "RETURNING id,stock_id,name,created_at",
            new MapSqlParameterSource("actor", actorId).addValue("stock", stockId)
                .addValue("name", validName), TYPE_ROW);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    /** Monolith: ProductTypes.tsx and useProductTypes.ts#updateProductType. */
    @Transactional
    public Map<String, Object> updateType(UUID actorId, UUID stockId, UUID typeId, String name) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<Map<String, Object>> rows = jdbc.query(
            "UPDATE public.product_types SET name=:name WHERE id=:id AND stock_id=:stock "
                + "AND EXISTS(SELECT 1 FROM public.stocks WHERE id=:stock AND user_id=:actor) "
                + "RETURNING id,stock_id,name,created_at",
            new MapSqlParameterSource("actor", actorId).addValue("stock", stockId)
                .addValue("id", typeId).addValue("name", cleanName(name, 200)), TYPE_ROW);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    /** Monolith: ProductTypes.tsx and useProductTypes.ts#deleteProductType. */
    @Transactional
    public void deleteType(UUID actorId, UUID stockId, UUID typeId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> deleted = jdbc.query(
            "DELETE FROM public.product_types WHERE id=:id AND stock_id=:stock "
                + "AND EXISTS(SELECT 1 FROM public.stocks WHERE id=:stock AND user_id=:actor) "
                + "RETURNING id",
            new MapSqlParameterSource("actor", actorId).addValue("stock", stockId)
                .addValue("id", typeId), (rs, row) -> rs.getObject(1, UUID.class));
        if (deleted.isEmpty()) throw missing();
    }

    private static String cleanName(String name, int limit) {
        if (name == null || name.isBlank() || name.trim().length() > limit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Informe um nome de até " + limit + " caracteres.");
        }
        return name.trim();
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado neste estoque.");
    }
}
