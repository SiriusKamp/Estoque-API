package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

class KitImageServiceTests {
    @Test
    void rejectsForeignStockBeforeStorageUpload() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SupabaseGateway storage = mock(SupabaseGateway.class);
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of());
        KitImageService service = new KitImageService(jdbc, storage);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.upload(UUID.randomUUID(), UUID.randomUUID(), "jwt", webp()));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(storage, never()).storageUpload(any(), any(), any());
    }

    @Test
    void referencedImageCannotBeDeleted() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SupabaseGateway storage = mock(SupabaseGateway.class);
        UUID actor = UUID.randomUUID(), stock = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        when(jdbc.queryForObject(startsWith("SELECT count(*) FROM public.product_kit_images"),
            any(SqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Long.class)))
            .thenReturn(1L);
        KitImageService service = new KitImageService(jdbc, storage);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.delete(actor, stock, "jwt", actor + "/" + stock + "/" + UUID.randomUUID() + ".webp"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(storage, never()).storageDelete(any(), any());
    }

    @Test
    void validatesWebpAndGeneratedPath() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SupabaseGateway storage = mock(SupabaseGateway.class);
        UUID actor = UUID.randomUUID(), stock = UUID.randomUUID();
        when(jdbc.query(startsWith("SELECT id FROM public.stocks"), any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any())).thenReturn(List.of(stock));
        KitImageService service = new KitImageService(jdbc, storage);
        byte[] image = webp();

        assertTrue(KitImageService.validWebp(image));
        assertFalse(KitImageService.validWebp(new byte[] { 1, 2, 3 }));
        String path = service.upload(actor, stock, "jwt", image);
        assertTrue(path.startsWith(actor + "/" + stock + "/"));
        assertTrue(path.endsWith(".webp"));
        verify(storage).storageUpload(path, "jwt", image);
    }

    private static byte[] webp() {
        return new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };
    }
}
