package com.Pedidos.Estoque.api;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Applies the verified HTTP identity to the current JDBC transaction before RLS queries. */
final class DatabaseActorContext {
    static final String READ_ACTOR_SQL =
        "SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid";

    private DatabaseActorContext() { }

    static void apply(NamedParameterJdbcTemplate jdbc, UUID actorId) {
        jdbc.queryForObject("SELECT set_config('request.jwt.claim.sub',:actor,true)",
            new MapSqlParameterSource("actor", actorId.toString()), String.class);
    }

    static UUID currentActor(NamedParameterJdbcTemplate jdbc) {
        return jdbc.queryForObject(READ_ACTOR_SQL, new MapSqlParameterSource(), UUID.class);
    }
}
