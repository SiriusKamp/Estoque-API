package com.Pedidos.Estoque.api;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Applies the verified HTTP identity to the current JDBC transaction before RLS queries. */
final class DatabaseActorContext {
    private DatabaseActorContext() { }

    static void apply(NamedParameterJdbcTemplate jdbc, UUID actorId) {
        jdbc.queryForObject("SELECT set_config('request.jwt.claim.sub',:actor,true)",
            new MapSqlParameterSource("actor", actorId.toString()), String.class);
    }
}
