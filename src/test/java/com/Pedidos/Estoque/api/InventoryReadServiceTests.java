package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

class InventoryReadServiceTests {
    @Test
    void foreignStockIsRejectedBeforeTheCatalogIsRead() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any()))
            .thenReturn(List.of());

        InventoryReadService service = new InventoryReadService(jdbc);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.products(UUID.randomUUID(), UUID.randomUUID(), 0, 30,
                "", "all", "all", "all", null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        InOrder order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(startsWith("SELECT set_config"),
            any(SqlParameterSource.class), eq(String.class));
        order.verify(jdbc).query(startsWith("SELECT id FROM public.stocks"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any());
        verify(jdbc, never()).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
    }
}
