package com.Pedidos.Estoque.api;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

@Service
@Transactional(readOnly = true)
public class InventoryReadService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String CONTAINS = " LIKE :pattern ESCAPE '!'";
    private static final RowMapper<Map<String, Object>> JSON_ROW = (rs, rowNumber) -> {
        ResultSetMetaData metadata = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            Object value = rs.getObject(index);
            if (value instanceof Timestamp timestamp) value = timestamp.toInstant().toString();
            else if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
            else if (value instanceof OffsetDateTime dateTime) value = dateTime.toInstant().toString();
            else if (value instanceof UUID uuid) value = uuid.toString();
            row.put(metadata.getColumnLabel(index), value);
        }
        return row;
    };

    private final NamedParameterJdbcTemplate jdbc;

    public InventoryReadService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Monolith: src/hooks/useStocks.tsx StockProvider query; GET /api/v1/stocks. */
    public List<Map<String, Object>> stocks(UUID actorId) {
        DatabaseActorContext.apply(jdbc, actorId);
        return jdbc.query("SELECT id, name, user_id, created_at FROM public.stocks "
            + "WHERE user_id = :actor ORDER BY created_at, id",
            new MapSqlParameterSource("actor", actorId), JSON_ROW);
    }

    /** Monolith: useInventoryRows('product_types') and ProductTypes.tsx exportAll. */
    public ApiPage<Map<String, Object>> productTypes(UUID actorId, UUID stockId,
            int page, int size, String search) {
        requireOwnedStock(actorId, stockId);
        String where = " WHERE stock_id = :stock";
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId);
        if (search != null && !search.isBlank()) {
            where += " AND name_search" + CONTAINS;
            params.addValue("pattern", ContainsSearch.pattern(search));
        }
        return page("public.product_type_catalog", where, "name, id", params, page, size);
    }

    /** Monolith: src/hooks/useProducts.ts#useProducts; products and kits share this catalog. */
    public ApiPage<Map<String, Object>> products(UUID actorId, UUID stockId, int page,
            int size, String search, String kind, String active, String availability, UUID typeId) {
        requireOwnedStock(actorId, stockId);
        StringBuilder where = new StringBuilder(" WHERE stock_id = :stock");
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId);
        if (search != null && !search.isBlank()) {
            where.append(" AND search_text").append(CONTAINS);
            params.addValue("pattern", ContainsSearch.pattern(search));
        }
        switch (kind) {
            case "products" -> where.append(" AND is_kit = false");
            case "kits" -> where.append(" AND is_kit = true");
            case "all" -> { }
            default -> throw badFilter("kind");
        }
        switch (active) {
            case "active" -> where.append(" AND active = true");
            case "inactive" -> where.append(" AND active = false");
            case "all" -> { }
            default -> throw badFilter("active");
        }
        switch (availability) {
            case "available" -> where.append(" AND base_quantity > 0");
            case "empty" -> where.append(" AND base_quantity = 0");
            case "all" -> { }
            default -> throw badFilter("availability");
        }
        if (typeId != null) {
            where.append(" AND type_id = :typeId");
            params.addValue("typeId", typeId);
        }
        return page("public.product_catalog", where.toString(), "name, id", params, page, size);
    }

    /** Monolith: src/hooks/useProducts.ts#useSuppliers; stock-scoped contains search. */
    public ApiPage<Map<String, Object>> suppliers(UUID actorId, UUID stockId,
            int page, int size, String search) {
        requireOwnedStock(actorId, stockId);
        String where = " WHERE stock_id = :stock";
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId);
        if (search != null && !search.isBlank()) {
            where += " AND lower(name)" + CONTAINS;
            params.addValue("pattern", ContainsSearch.pattern(search));
        }
        return page("public.suppliers", where, "name, id", params, page, size);
    }

    /** Monolith: Products.tsx#ProductEditor competitor_prices query. */
    public ApiPage<Map<String, Object>> competitorPrices(UUID actorId, UUID stockId,
            UUID productId, int page, int size) {
        requireOwnedStock(actorId, stockId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho inválido.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId)
            .addValue("product", productId).addValue("limit", size).addValue("offset", page * size);
        String from = " FROM public.competitor_prices c JOIN public.products p ON p.id=c.product_id "
            + "WHERE p.stock_id=:stock AND c.product_id=:product";
        Long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT c.competitor_name,c.price" + from + " ORDER BY c.id LIMIT :limit OFFSET :offset",
            params, JSON_ROW);
        return new ApiPage<>(rows, total == null ? 0 : total, page, size);
    }

    /** Monolith: useInventoryRows('lot_details') and Lots.tsx filters. */
    public ApiPage<Map<String, Object>> lots(UUID actorId, UUID stockId, int page,
            int size, String search, UUID productId, String balance, String expiry,
            Instant from, Instant to) {
        requireOwnedStock(actorId, stockId);
        StringBuilder where = new StringBuilder(" WHERE stock_id = :stock");
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId);
        appendSearch(where, params, search);
        if (productId != null) {
            where.append(" AND product_id = :productId");
            params.addValue("productId", productId);
        }
        switch (balance) {
            case "available" -> where.append(" AND quantity_remaining > 0");
            case "empty" -> where.append(" AND quantity_remaining = 0");
            case "all" -> { }
            default -> throw badFilter("balance");
        }
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        switch (expiry) {
            case "expired" -> {
                where.append(" AND expires_on < :today");
                params.addValue("today", today);
            }
            case "soon" -> {
                where.append(" AND expires_on BETWEEN :today AND :soon");
                params.addValue("today", today).addValue("soon", today.plusDays(30));
            }
            case "all" -> { }
            default -> throw badFilter("expiry");
        }
        appendDates(where, params, "acquired_at", from, to);
        return page("public.lot_details", where.toString(), "created_at, id", params, page, size);
    }

    /** Monolith: useInventoryRows('movement_details') and Movements.tsx filters. */
    public ApiPage<Map<String, Object>> movements(UUID actorId, UUID stockId, int page,
            int size, String search, UUID productId, String kind, Instant from, Instant to) {
        requireOwnedStock(actorId, stockId);
        StringBuilder where = new StringBuilder(" WHERE stock_id = :stock");
        MapSqlParameterSource params = new MapSqlParameterSource("stock", stockId);
        appendSearch(where, params, search);
        if (productId != null) {
            where.append(" AND product_id = :productId");
            params.addValue("productId", productId);
        }
        switch (kind) {
            case "IN", "OUT", "PRODUCE" -> {
                where.append(" AND kind = :kind");
                params.addValue("kind", kind);
            }
            case "", "all" -> { }
            default -> throw badFilter("kind");
        }
        appendDates(where, params, "occurred_at", from, to);
        return page("public.movement_details", where.toString(), "created_at, id", params, page, size);
    }

    private static void appendSearch(StringBuilder where, MapSqlParameterSource params,
            String search) {
        if (search != null && !search.isBlank()) {
            where.append(" AND search_text").append(CONTAINS);
            params.addValue("pattern", ContainsSearch.pattern(search));
        }
    }

    private static void appendDates(StringBuilder where, MapSqlParameterSource params,
            String column, Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo de datas inválido.");
        }
        if (from != null) {
            where.append(" AND ").append(column).append(" >= :from");
            params.addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" AND ").append(column).append(" < :to");
            params.addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        }
    }

    private void requireOwnedStock(UUID actorId, UUID stockId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> matches = jdbc.query("SELECT id FROM public.stocks "
                + "WHERE id = :stock AND user_id = :actor",
            new MapSqlParameterSource("stock", stockId).addValue("actor", actorId),
            (rs, index) -> rs.getObject(1, UUID.class));
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estoque não encontrado.");
        }
    }

    private ApiPage<Map<String, Object>> page(String view, String where, String order,
            MapSqlParameterSource params, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho inválido.");
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + view + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", page * size);
        List<Map<String, Object>> rows = jdbc.query("SELECT * FROM " + view + where
            + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params, JSON_ROW);
        return new ApiPage<>(rows, total == null ? 0 : total, page, size);
    }

    private static ResponseStatusException badFilter(String name) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro inválido: " + name);
    }
}
