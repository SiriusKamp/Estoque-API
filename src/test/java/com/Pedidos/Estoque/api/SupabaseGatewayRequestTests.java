package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SupabaseGatewayRequestTests {
    @Test
    void userValidationSendsProjectKeyAndUserBearerToSupabaseAuth() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{\"id\":\"user-id\"}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(reply);
        SupabaseGateway gateway = new SupabaseGateway(new ObjectMapper(),
            "https://project.test", "sb_publishable_example", client);

        assertEquals("user-id", gateway.auth("GET", "/user", "user-jwt", null)
            .path("id").asText());
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://project.test/auth/v1/user", sent.getValue().uri().toString());
        assertEquals("GET", sent.getValue().method());
        assertEquals("sb_publishable_example", sent.getValue().headers().firstValue("apikey").orElseThrow());
        assertEquals("Bearer user-jwt", sent.getValue().headers()
            .firstValue("Authorization").orElseThrow());
    }

    @Test
    void publishableKeyIsNotUsedAsBearerForUnauthenticatedLogin() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(reply);
        SupabaseGateway gateway = new SupabaseGateway(new ObjectMapper(),
            "https://project.test", "sb_publishable_example", client);

        gateway.auth("POST", "/token?grant_type=password", null,
            java.util.Map.of("email", "user@example.test", "password", "secret"));

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("sb_publishable_example", sent.getValue().headers()
            .firstValue("apikey").orElseThrow());
        assertEquals(false, sent.getValue().headers().firstValue("Authorization").isPresent());
    }

    @Test
    void legacyAnonJwtRemainsBearerForUnauthenticatedLogin() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(reply);
        SupabaseGateway gateway = new SupabaseGateway(new ObjectMapper(),
            "https://project.test", "legacy-anon-jwt", client);

        gateway.auth("POST", "/token?grant_type=password", null,
            java.util.Map.of("email", "user@example.test", "password", "secret"));

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer legacy-anon-jwt", sent.getValue().headers()
            .firstValue("Authorization").orElseThrow());
    }
}
