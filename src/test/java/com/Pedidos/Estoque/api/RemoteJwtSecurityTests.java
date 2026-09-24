package com.Pedidos.Estoque.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(InventoryReadController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "stock.jwt.mode=remote",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.test/auth/v1"
})
class RemoteJwtSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryReadService service;
    @MockitoBean SupabaseGateway supabase;

    @Test
    void verifiedSupabaseUserReachesStockControllerAsJwtPrincipal() throws Exception {
        UUID actor = UUID.randomUUID();
        String token = token(actor);
        when(supabase.auth("GET", "/user", token, null)).thenReturn(
            new ObjectMapper().readTree("{\"id\":\"" + actor + "\"}"));
        when(service.stocks(actor)).thenReturn(List.of());

        mvc.perform(get("/api/v1/stocks")
                .header("Origin", "http://localhost:5173")
                .header("Authorization", "Bearer " + token))
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(status().isOk());
        verify(service).stocks(actor);
        verify(supabase).auth("GET", "/user", token, null);
    }

    @Test
    void authRejectionIsUnauthorizedAndAuthOutageIsUnavailable() throws Exception {
        String token = token(UUID.randomUUID());
        when(supabase.auth("GET", "/user", token, null)).thenThrow(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/v1/stocks").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());

        reset(supabase);
        when(supabase.auth("GET", "/user", token, null)).thenThrow(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(get("/api/v1/stocks").header("Authorization", "Bearer " + token))
            .andExpect(status().isServiceUnavailable());

        reset(supabase);
        when(supabase.auth("GET", "/user", token, null)).thenThrow(
            new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS));
        mvc.perform(get("/api/v1/stocks").header("Authorization", "Bearer " + token))
            .andExpect(status().isServiceUnavailable());
    }

    private static String token(UUID actor) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://project.test/auth/v1")
            .subject(actor.toString()).audience("authenticated")
            .claim("role", "authenticated")
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("local-test-key-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
