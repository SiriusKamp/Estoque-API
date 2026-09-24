package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class AuthControllerTests {
    @Test
    void loginForwardsCredentialsToPasswordGrant() {
        SupabaseGateway gateway = mock(SupabaseGateway.class);
        AuthController controller = new AuthController(gateway, "https://example.test/auth");
        when(gateway.auth(eq("POST"), eq("/token?grant_type=password"),
            eq(null), any())).thenReturn(new ObjectMapper().readTree("{\"access_token\":\"jwt\"}"));

        assertEquals("jwt", controller.login(new AuthController.Credentials("a@b.test", "secret"))
            .get("access_token").asText());
        verify(gateway).auth("POST", "/token?grant_type=password", null,
            Map.of("email", "a@b.test", "password", "secret"));
    }

    @Test
    void registrationUsesFixedRedirectAndDisplayName() {
        SupabaseGateway gateway = mock(SupabaseGateway.class);
        AuthController controller = new AuthController(gateway, "https://example.test/auth");
        controller.register(new AuthController.Registration("a@b.test", "secret", "Sirius"));
        verify(gateway).auth("POST",
            "/signup?redirect_to=https%3A%2F%2Fexample.test%2Fauth", null,
            Map.of("email", "a@b.test", "password", "secret",
                "data", Map.of("display_name", "Sirius")));
    }

    @Test
    void invalidRedirectAndEmailAreRejected() {
        SupabaseGateway gateway = mock(SupabaseGateway.class);
        assertThrows(IllegalArgumentException.class,
            () -> new AuthController(gateway, "https://example.test/other"));
        AuthController controller = new AuthController(gateway, "https://example.test/auth");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> controller.login(new AuthController.Credentials("invalid", "secret")));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
