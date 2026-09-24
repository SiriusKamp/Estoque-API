package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

class CatalogWriteServiceTests {
    @Test
    void rejectsBlankStockNameBeforeInsert() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        CatalogWriteService service = new CatalogWriteService(jdbc);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createStock(UUID.randomUUID(), "  "));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(jdbc, never()).query(startsWith("INSERT INTO public.stocks"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any());
    }

    @Test
    void refusesTypeUpdateWhenStockOwnerOrTypeDoesNotMatch() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(startsWith("UPDATE public.product_types"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any()))
            .thenReturn(List.of());
        CatalogWriteService service = new CatalogWriteService(jdbc);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateType(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Bebidas"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc).query(startsWith("UPDATE public.product_types"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<Map<String, Object>>>any());
    }
}
