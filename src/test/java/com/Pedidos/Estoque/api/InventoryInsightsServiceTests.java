package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class InventoryInsightsServiceTests {
    @Test
    void foreignStockCannotCallDashboardRpc() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of());
        InventoryInsightsService service = new InventoryInsightsService(jdbc, new ObjectMapper());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.dashboard(UUID.randomUUID(), UUID.randomUUID(), 7));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).queryForObject(startsWith("SELECT public.inventory_dashboard"),
            any(SqlParameterSource.class), eq(String.class));
    }

    @Test
    void dashboardUsesVerifiedTransactionLocalActorBeforeRpc() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID actor = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        when(jdbc.queryForObject(startsWith("SELECT set_config"), any(SqlParameterSource.class),
            eq(String.class))).thenReturn(actor.toString());
        when(jdbc.queryForObject(eq(DatabaseActorContext.READ_ACTOR_SQL), any(SqlParameterSource.class),
            eq(UUID.class))).thenReturn(actor);
        when(jdbc.queryForObject(startsWith("SELECT public.inventory_dashboard"),
            any(SqlParameterSource.class), eq(String.class))).thenReturn("{\"products\":2}");
        InventoryInsightsService service = new InventoryInsightsService(jdbc, new ObjectMapper());

        assertEquals(2, service.dashboard(actor, stock, 30).get("products").asInt());
        verify(jdbc).queryForObject(startsWith("SELECT public.inventory_dashboard"),
            any(SqlParameterSource.class), eq(String.class));
    }

    @Test
    void invalidRecipeAndOversizedKitBatchAreRejectedBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        InventoryInsightsService service = new InventoryInsightsService(jdbc, new ObjectMapper());
        UUID product = UUID.randomUUID();
        var repeated = List.of(new InventoryDecrementRequest.Item(product, BigDecimal.ONE, "un"),
            new InventoryDecrementRequest.Item(product, BigDecimal.ONE, "un"));
        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
            () -> service.estimateRecipe(UUID.randomUUID(), UUID.randomUUID(), repeated));
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.getStatusCode());
        ResponseStatusException oversized = assertThrows(ResponseStatusException.class,
            () -> service.kitCapacities(UUID.randomUUID(), UUID.randomUUID(),
                java.util.Collections.nCopies(101, UUID.randomUUID())));
        assertEquals(HttpStatus.BAD_REQUEST, oversized.getStatusCode());
        verify(jdbc, never()).query(startsWith("SELECT id FROM public.stocks"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
    }
}
