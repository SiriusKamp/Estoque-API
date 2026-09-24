package com.Pedidos.Estoque.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryReadController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {
    @Autowired MockMvc mvc;
    @MockitoBean InventoryReadService service;
    @MockitoBean JwtDecoder decoder;

    @Test
    void browserPreflightAllowsConfiguredOriginAndAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/v1/stocks")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Headers", "authorization, content-type"));
    }

    @Test
    void stockEndpointRequiresBearerTokenAndUsesVerifiedSubject() throws Exception {
        mvc.perform(get("/api/v1/stocks")).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get("/api/v1/stocks").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());

        UUID actor = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("valid")
            .header("alg", "RS256")
            .subject(actor.toString())
            .claim("aud", List.of("authenticated"))
            .build();
        when(decoder.decode("valid")).thenReturn(jwt);
        when(service.stocks(actor)).thenReturn(List.of());
        mvc.perform(get("/api/v1/stocks").header("Authorization", "Bearer valid"))
            .andExpect(status().isOk());
        verify(service).stocks(actor);
    }
}
