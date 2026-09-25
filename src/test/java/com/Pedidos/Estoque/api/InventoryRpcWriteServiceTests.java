package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class InventoryRpcWriteServiceTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void invalidBatchIsRejectedBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        InventoryRpcWriteService service = new InventoryRpcWriteService(jdbc, json);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.importBatch(UUID.randomUUID(), UUID.randomUUID(), "unknown",
                json.readTree("[{}]"), false));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(jdbc, never()).queryForObject(any(String.class), any(SqlParameterSource.class),
            eq(String.class));
    }

    @Test
    void foreignStockCannotReceiveLots() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of());
        InventoryRpcWriteService service = new InventoryRpcWriteService(jdbc, json);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.receiveLots(UUID.randomUUID(), UUID.randomUUID(), "entrada:1",
                json.readTree("{\"items\":[{}]}")));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(startsWith("SELECT set_config"),
            any(SqlParameterSource.class), eq(String.class));
        order.verify(jdbc).query(startsWith("SELECT id FROM public.stocks"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
        verify(jdbc, never()).queryForObject(startsWith("SELECT public.receive_lots"),
            any(SqlParameterSource.class), eq(UUID.class));
    }

    @Test
    void productionCallsExistingLedgerWithVerifiedDatabaseActor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID actor = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        when(jdbc.queryForObject(startsWith("SELECT set_config"), any(SqlParameterSource.class),
            eq(String.class))).thenReturn(actor.toString());
        when(jdbc.queryForObject(eq(DatabaseActorContext.READ_ACTOR_SQL), any(SqlParameterSource.class),
            eq(UUID.class))).thenReturn(actor);
        when(jdbc.queryForObject(startsWith("SELECT public.produce_kit"),
            any(SqlParameterSource.class), eq(UUID.class))).thenReturn(operation);
        InventoryRpcWriteService service = new InventoryRpcWriteService(jdbc, json);

        assertEquals(operation, service.produceKit(actor, stock, "producao:1",
            json.readTree("{\"kit_id\":\"" + UUID.randomUUID() + "\",\"quantity\":1,\"unit\":\"un\"}")));
        verify(jdbc).queryForObject(startsWith("SELECT public.produce_kit"),
            any(SqlParameterSource.class), eq(UUID.class));
    }
}
