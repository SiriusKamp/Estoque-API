package com.Pedidos.Estoque.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server-side Supabase Auth and private Storage transport; never accepts an arbitrary URL. */
@Component
public class SupabaseGateway {
    private volatile HttpClient http;
    private final ObjectMapper json;
    private final String origin;
    private final String publishableKey;

    @Autowired
    public SupabaseGateway(ObjectMapper json,
            @Value("${stock.supabase.url:}") String origin,
            @Value("${stock.supabase.publishable-key:}") String publishableKey) {
        this(json, origin, publishableKey, null);
    }

    SupabaseGateway(ObjectMapper json, String origin, String publishableKey,
            HttpClient client) {
        this.json = json;
        this.origin = origin.strip().replaceAll("/+$", "");
        this.publishableKey = publishableKey.strip();
        this.http = client;
    }

    public JsonNode auth(String method, String path, String token, Object body) {
        Response reply = exchange(method, "/auth/v1" + path, token, "application/json",
            body == null ? null : encode(body), false);
        return decode(reply.bytes());
    }

    public void storageUpload(String path, String token, byte[] image) {
        exchange("POST", "/storage/v1/object/kit-images/" + path, token,
            "image/webp", image, true);
    }

    public byte[] storageRead(String path, String token) {
        return exchange("GET", "/storage/v1/object/authenticated/kit-images/" + path,
            token, null, null, true).bytes();
    }

    public void storageDelete(String path, String token) {
        exchange("DELETE", "/storage/v1/object/kit-images/" + path,
            token, null, null, true);
    }

    private Response exchange(String method, String path, String token, String contentType,
            byte[] body, boolean storage) {
        if (!origin.matches("https?://[^/]+") || publishableKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Conexão do Supabase não configurada na API.");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path))
            .timeout(Duration.ofSeconds(30))
            .header("apikey", publishableKey);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        } else if (!publishableKey.startsWith("sb_")) {
            // Legacy anon keys are JWTs; new sb_publishable_* keys are not.
            request.header("Authorization", "Bearer " + publishableKey);
        }
        if (contentType != null) request.header("Content-Type", contentType);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<byte[]> reply = client().send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (reply.statusCode() >= 200 && reply.statusCode() < 300) return new Response(reply.body());
            HttpStatus status = switch (reply.statusCode()) {
                case 400 -> HttpStatus.BAD_REQUEST;
                case 401 -> HttpStatus.UNAUTHORIZED;
                case 403 -> HttpStatus.FORBIDDEN;
                case 404 -> HttpStatus.NOT_FOUND;
                case 409 -> HttpStatus.CONFLICT;
                case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
                case 429 -> HttpStatus.TOO_MANY_REQUESTS;
                default -> HttpStatus.BAD_GATEWAY;
            };
            String detail = storage ? "Não foi possível acessar a imagem do kit."
                : authError(reply.body());
            throw new ResponseStatusException(status, detail);
        } catch (IOException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Supabase indisponível no momento.", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Consulta ao Supabase interrompida.", failure);
        }
    }

    private HttpClient client() {
        HttpClient current = http;
        if (current == null) {
            synchronized (this) {
                current = http;
                if (current == null) http = current = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            }
        }
        return current;
    }

    private String authError(byte[] bytes) {
        JsonNode value = decode(bytes);
        for (String field : new String[] { "msg", "error_description", "message" }) {
            JsonNode message = value.path(field);
            if (message.isTextual() && message.asText().length() <= 200) return message.asText();
        }
        return "Não foi possível concluir a autenticação.";
    }

    private byte[] encode(Object value) {
        try {
            return json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Falha ao preparar solicitação ao Supabase.", failure);
        }
    }

    private JsonNode decode(byte[] bytes) {
        try {
            return json.readTree(bytes.length == 0 ? "{}" : new String(bytes, StandardCharsets.UTF_8));
        } catch (JacksonException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Resposta inválida do Supabase.", failure);
        }
    }

    private record Response(byte[] bytes) { }
}
