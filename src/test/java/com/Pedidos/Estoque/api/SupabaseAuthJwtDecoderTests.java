package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class SupabaseAuthJwtDecoderTests {
    private static final String ISSUER = "https://project.test/auth/v1";
    private static final byte[] TEST_SECRET = "local-test-key-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsOnlyTokenWhoseSubjectMatchesAuthenticatedSupabaseUser() throws Exception {
        SupabaseGateway supabase = mock(SupabaseGateway.class);
        UUID actor = UUID.randomUUID();
        String token = token(actor, ISSUER, "authenticated", "authenticated", Instant.now().plusSeconds(3600));
        when(supabase.auth("GET", "/user", token, null)).thenReturn(
            new ObjectMapper().readTree("{\"id\":\"" + actor + "\"}"));

        var jwt = new SupabaseAuthJwtDecoder(supabase, ISSUER).decode(token);

        assertEquals(actor.toString(), jwt.getSubject());
        assertEquals("authenticated", jwt.getClaimAsString("role"));
        verify(supabase).auth("GET", "/user", token, null);
    }

    @Test
    void rejectsMismatchedUserAndAuthRejection() throws Exception {
        SupabaseGateway supabase = mock(SupabaseGateway.class);
        String token = token(UUID.randomUUID(), ISSUER, "authenticated", "authenticated",
            Instant.now().plusSeconds(3600));
        when(supabase.auth("GET", "/user", token, null)).thenReturn(
            new ObjectMapper().readTree("{\"id\":\"" + UUID.randomUUID() + "\"}"));
        assertThrows(BadJwtException.class,
            () -> new SupabaseAuthJwtDecoder(supabase, ISSUER).decode(token));

        when(supabase.auth("GET", "/user", token, null)).thenThrow(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(BadJwtException.class,
            () -> new SupabaseAuthJwtDecoder(supabase, ISSUER).decode(token));
    }

    @Test
    void rejectsAnonymousWrongIssuerAndExpiredBeforeRemoteCall() throws Exception {
        SupabaseGateway supabase = mock(SupabaseGateway.class);
        SupabaseAuthJwtDecoder decoder = new SupabaseAuthJwtDecoder(supabase, ISSUER);
        UUID actor = UUID.randomUUID();

        assertThrows(BadJwtException.class, () -> decoder.decode(
            token(actor, ISSUER, "authenticated", "anon", Instant.now().plusSeconds(3600))));
        assertThrows(BadJwtException.class, () -> decoder.decode(
            token(actor, ISSUER, "anon", "authenticated", Instant.now().plusSeconds(3600))));
        assertThrows(BadJwtException.class, () -> decoder.decode(
            token(actor, "https://other.test/auth/v1", "authenticated", "authenticated",
                Instant.now().plusSeconds(3600))));
        assertThrows(BadJwtException.class, () -> decoder.decode(
            token(actor, ISSUER, "authenticated", "authenticated", Instant.now().minusSeconds(1))));
        verify(supabase, never()).auth(eq("GET"), eq("/user"),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void authOutageIsNotClassifiedAsAnInvalidSession() throws Exception {
        SupabaseGateway supabase = mock(SupabaseGateway.class);
        String token = token(UUID.randomUUID(), ISSUER, "authenticated", "authenticated",
            Instant.now().plusSeconds(3600));
        when(supabase.auth("GET", "/user", token, null)).thenThrow(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        JwtException error = assertThrows(JwtException.class,
            () -> new SupabaseAuthJwtDecoder(supabase, ISSUER).decode(token));
        assertEquals("Validação de sessão indisponível.", error.getMessage());
    }

    private static String token(UUID actor, String issuer, String audience, String role,
            Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer(issuer).subject(actor.toString()).audience(audience)
            .claim("role", role).expirationTime(Date.from(expiresAt))
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET));
        return jwt.serialize();
    }
}
