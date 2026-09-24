package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

class StockApiClientServiceTests {
    @Test
    void storesOnlyAHashAndScopesTheKeyLookupToItsStock() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        UUID owner = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        StockApiClientService service = new StockApiClientService(jdbc);

        StockApiClientService.CreatedClient client = service.create(owner, stock, "Comandas");

        assertTrue(client.key().matches("stk_[A-Za-z0-9_-]{43}"));
        ArgumentCaptor<SqlParameterSource> inserted = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(startsWith("INSERT INTO public.stock_api_clients"), inserted.capture());
        assertEquals(stock, inserted.getValue().getValue("stock"));
        assertNotEquals(client.key(), inserted.getValue().getValue("hash"));
        assertEquals(64, ((String) inserted.getValue().getValue("hash")).length());

        when(jdbc.query(startsWith("SELECT client_id,stock_owner_id"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<StockApiClientService.ClientIdentity>>any()))
            .thenReturn(List.of());
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
            () -> service.authenticate(UUID.randomUUID(), client.key()));
        assertEquals(HttpStatus.UNAUTHORIZED, denied.getStatusCode());
        verify(jdbc).query(startsWith("SELECT client_id,stock_owner_id"),
            any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<StockApiClientService.ClientIdentity>>any());
    }
}
