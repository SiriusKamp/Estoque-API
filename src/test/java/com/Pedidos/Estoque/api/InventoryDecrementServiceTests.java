package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

class InventoryDecrementServiceTests {
    @Test
    void carriesIntegrationIdentityAndMeasuredQuantityToTheImmutablePayload() {
        UUID product = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        InventoryDecrementRequest request = new InventoryDecrementRequest("pedido:1:concluido", "Pedido 1",
            null, List.of(new InventoryDecrementRequest.Item(product, new BigDecimal("100.5"), "ml")));

        Map<String, Object> payload = InventoryDecrementService.validatedPayload(request, client);

        assertEquals("stock-api-client", payload.get("source"));
        assertEquals(payload, InventoryDecrementService.validatedPayload(request, UUID.randomUUID()));
        assertEquals("ml", ((Map<?, ?>) ((List<?>) payload.get("items")).getFirst()).get("unit"));
    }

    @Test
    void rejectsInvalidAmountsBeforeWritingInventory() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        InventoryDecrementService service = new InventoryDecrementService(jdbc, new ObjectMapper());
        InventoryDecrementRequest request = new InventoryDecrementRequest("pedido:1", "Pedido 1", null,
            List.of(new InventoryDecrementRequest.Item(UUID.randomUUID(), new BigDecimal("0"), "ml")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.decrement(UUID.randomUUID(), UUID.randomUUID(), request, null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(jdbc, never()).queryForObject(any(String.class), any(SqlParameterSource.class), eq(UUID.class));
    }

    @Test
    void rejectsForeignStockAfterSettingDatabaseIdentity() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of());
        InventoryDecrementService service = new InventoryDecrementService(jdbc, new ObjectMapper());
        InventoryDecrementRequest request = new InventoryDecrementRequest("pedido:1", "Pedido 1", null,
            List.of(new InventoryDecrementRequest.Item(UUID.randomUUID(), BigDecimal.ONE, "un")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.decrement(UUID.randomUUID(), UUID.randomUUID(), request, null));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc).queryForObject(startsWith("SELECT set_config"),
            any(SqlParameterSource.class), eq(String.class));
    }

    @Test
    void passesVerifiedActorThroughTransactionLocalClaimThenCallsLedger() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID actor = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        when(jdbc.queryForObject(startsWith("SELECT set_config"), any(SqlParameterSource.class),
            eq(String.class))).thenReturn(actor.toString());
        when(jdbc.queryForObject(eq("SELECT auth.uid()"), any(SqlParameterSource.class),
            eq(UUID.class))).thenReturn(actor);
        when(jdbc.queryForObject(startsWith("SELECT public.decrement_inventory"),
            any(SqlParameterSource.class), eq(UUID.class))).thenReturn(operation);
        InventoryDecrementService service = new InventoryDecrementService(jdbc, new ObjectMapper());
        InventoryDecrementRequest request = new InventoryDecrementRequest("pedido:1", "Pedido 1", null,
            List.of(new InventoryDecrementRequest.Item(UUID.randomUUID(), BigDecimal.ONE, "un")));

        assertEquals(operation, service.decrement(actor, stock, request, null));
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(startsWith("SELECT public.decrement_inventory"),
            parameters.capture(), eq(UUID.class));
        assertEquals(stock, parameters.getValue().getValue("stock"));
        assertTrue(((String) parameters.getValue().getValue("data")).contains("Pedido 1"));
    }
}
