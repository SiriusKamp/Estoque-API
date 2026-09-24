package com.Pedidos.Estoque.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockApiClientService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final NamedParameterJdbcTemplate jdbc;

    public StockApiClientService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A stock owner provisions one secret for the orders/comandas service. */
    @Transactional
    public CreatedClient create(UUID actorId, UUID stockId, String name) {
        requireOwner(actorId, stockId);
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome de até 100 caracteres.");
        }
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = "stk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.stock_api_clients(id,stock_id,name,key_hash,created_by) "
                + "VALUES(:id,:stock,:name,:hash,:actor)",
            new MapSqlParameterSource("id", id).addValue("stock", stockId)
                .addValue("name", name.trim()).addValue("hash", sha256(secret))
                .addValue("actor", actorId));
        return new CreatedClient(id, name.trim(), secret);
    }

    @Transactional
    public void revoke(UUID actorId, UUID stockId, UUID clientId) {
        requireOwner(actorId, stockId);
        int changed = jdbc.update("UPDATE public.stock_api_clients SET active = false "
                + "WHERE id = :id AND stock_id = :stock AND active",
            new MapSqlParameterSource("id", clientId).addValue("stock", stockId));
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credencial não encontrada.");
    }

    /** A service key is scoped to exactly one stock; no user-controlled actor is accepted. */
    @Transactional(readOnly = true)
    public ClientIdentity authenticate(UUID stockId, String secret) {
        if (secret == null || !secret.matches("stk_[A-Za-z0-9_-]{43}")) {
            throw unauthorized();
        }
        List<ClientIdentity> clients = jdbc.query(
            "SELECT client_id,stock_owner_id "
                + "FROM public.authenticate_stock_api_client(:stock,:hash)",
            new MapSqlParameterSource("stock", stockId).addValue("hash", sha256(secret)),
            (rs, row) -> new ClientIdentity(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)));
        if (clients.isEmpty()) throw unauthorized();
        return clients.getFirst();
    }

    private void requireOwner(UUID actorId, UUID stockId) {
        DatabaseActorContext.apply(jdbc, actorId);
        List<UUID> found = jdbc.query(
            "SELECT id FROM public.stocks WHERE id = :stock AND user_id = :actor",
            new MapSqlParameterSource("stock", stockId).addValue("actor", actorId),
            (rs, row) -> rs.getObject(1, UUID.class));
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estoque não encontrado.");
    }

    private static String sha256(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 indisponível.", unavailable);
        }
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de integração inválida.");
    }

    public record CreatedClient(UUID id, String name, String key) { }
    public record ClientIdentity(UUID id, UUID stockOwnerId) { }
}
