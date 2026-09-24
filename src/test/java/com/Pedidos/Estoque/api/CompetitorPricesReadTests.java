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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

class CompetitorPricesReadTests {
    @Test
    void foreignStockCannotReadCompetitorPrices() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of());
        InventoryReadService service = new InventoryReadService(jdbc);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.competitorPrices(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 0, 30));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).queryForObject(startsWith("SELECT count(*) FROM public.competitor_prices"),
            any(SqlParameterSource.class), eq(Long.class));
    }
}
