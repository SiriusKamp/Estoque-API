package com.Pedidos.Estoque.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Monolith: useKitImages, uploadKitImage and removeKitImage. The file stays in private Storage. */
@Service
@Transactional(readOnly = true)
public class KitImageService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Pattern PATH = Pattern.compile(
        "^[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.webp$");
    private final NamedParameterJdbcTemplate jdbc;
    private final SupabaseGateway storage;

    public KitImageService(NamedParameterJdbcTemplate jdbc, SupabaseGateway storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public List<Map<String, Object>> list(UUID actor, UUID stock, List<UUID> kitIds) {
        if (kitIds == null || kitIds.isEmpty() || kitIds.size() > 100 || kitIds.contains(null)) {
            throw bad("Informe de 1 a 100 kits.");
        }
        requireOwnedStock(actor, stock);
        return jdbc.query("SELECT i.product_kit_id,i.image_path FROM public.product_kit_images i "
                + "JOIN public.products p ON p.id=i.product_kit_id "
                + "JOIN public.stocks s ON s.id=p.stock_id AND s.user_id=:actor "
                + "WHERE p.stock_id=:stock AND p.is_kit AND p.id IN (:ids)",
            new MapSqlParameterSource("stock", stock).addValue("actor", actor).addValue("ids", kitIds),
            (rs, row) -> Map.of("product_kit_id",
                rs.getObject("product_kit_id", UUID.class).toString(),
                "image_path", rs.getString("image_path")));
    }

    public byte[] content(UUID actor, UUID stock, UUID kitId, String token) {
        requireOwnedStock(actor, stock);
        List<String> paths = jdbc.query("SELECT i.image_path FROM public.product_kit_images i "
                + "JOIN public.products p ON p.id=i.product_kit_id "
                + "JOIN public.stocks s ON s.id=p.stock_id AND s.user_id=:actor "
                + "WHERE p.id=:kit AND p.stock_id=:stock AND p.is_kit",
            new MapSqlParameterSource("kit", kitId).addValue("stock", stock).addValue("actor", actor),
            (rs, row) -> rs.getString(1));
        if (paths.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Imagem não encontrada.");
        return storage.storageRead(paths.getFirst(), token);
    }

    public String upload(UUID actor, UUID stock, String token, byte[] image) {
        requireOwnedStock(actor, stock);
        if (!validWebp(image)) throw bad("Envie WebP válido de até 5 MB.");
        String path = actor + "/" + stock + "/" + UUID.randomUUID() + ".webp";
        storage.storageUpload(path, token, image);
        return path;
    }

    public void delete(UUID actor, UUID stock, String token, String path) {
        requireOwnedStock(actor, stock);
        if (path == null || !PATH.matcher(path).matches()
                || !path.startsWith(actor + "/" + stock + "/")) throw bad("Caminho inválido.");
        Long used = jdbc.queryForObject("SELECT count(*) FROM public.product_kit_images "
                + "WHERE image_path=:path", new MapSqlParameterSource("path", path), Long.class);
        if (used != null && used > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "A imagem ainda pertence a um kit.");
        storage.storageDelete(path, token);
    }

    private void requireOwnedStock(UUID actor, UUID stock) {
        DatabaseActorContext.apply(jdbc, actor);
        List<UUID> owned = jdbc.query("SELECT id FROM public.stocks "
                + "WHERE id=:stock AND user_id=:actor",
            new MapSqlParameterSource("stock", stock).addValue("actor", actor),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (owned.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Estoque não encontrado.");
    }

    static boolean validWebp(byte[] bytes) {
        return bytes != null && bytes.length >= 12 && bytes.length <= MAX_BYTES
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
