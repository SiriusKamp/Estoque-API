package com.Pedidos.Estoque.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Monolith: Auth.tsx and useAuth.tsx; credentials go to Supabase Auth through Spring. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final SupabaseGateway supabase;
    private final String redirectUrl;

    public AuthController(SupabaseGateway supabase,
            @Value("${stock.auth.redirect-url}") String redirectUrl) {
        this.supabase = supabase;
        this.redirectUrl = validateRedirect(redirectUrl);
    }

    /** Monolith: Auth.tsx signInWithPassword. */
    @PostMapping({ "/login", "/Login" })
    public JsonNode login(@RequestBody Credentials request) {
        return supabase.auth("POST", "/token?grant_type=password", null,
            Map.of("email", email(request.email()), "password", password(request.password())));
    }

    /** Monolith: Auth.tsx signUp with display_name and confirmation email. */
    @PostMapping({ "/register", "/cadastro" })
    public JsonNode register(@RequestBody Registration request) {
        String name = request.displayName() == null ? "" : request.displayName().trim();
        if (name.isEmpty() || name.length() > 100) throw bad("Informe um nome de exibição válido.");
        return supabase.auth("POST", "/signup?redirect_to=" + encodedRedirect(), null,
            Map.of("email", email(request.email()), "password", password(request.password()),
                "data", Map.of("display_name", name)));
    }

    /** Monolith: useAuth.tsx automatic session refresh. */
    @PostMapping("/refresh")
    public JsonNode refresh(@RequestBody Refresh request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw bad("Sessão inválida.");
        }
        return supabase.auth("POST", "/token?grant_type=refresh_token", null,
            Map.of("refresh_token", request.refreshToken()));
    }

    /** Monolith: Auth.tsx resetPasswordForEmail. */
    @PostMapping("/recover")
    public JsonNode recover(@RequestBody Recovery request) {
        return supabase.auth("POST", "/recover?redirect_to=" + encodedRedirect(), null,
            Map.of("email", email(request.email())));
    }

    /** Monolith: useAuth.tsx getSession validation and email/recovery callback. */
    @GetMapping("/user")
    public JsonNode user(@AuthenticationPrincipal Jwt jwt) {
        return supabase.auth("GET", "/user", jwt.getTokenValue(), null);
    }

    /** Monolith: Auth.tsx updateUser({ password }). */
    @PutMapping("/password")
    public JsonNode password(@AuthenticationPrincipal Jwt jwt, @RequestBody NewPassword request) {
        return supabase.auth("PUT", "/user", jwt.getTokenValue(),
            Map.of("password", password(request.password())));
    }

    /** Monolith: useAuth.tsx signOut. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        supabase.auth("POST", "/logout", jwt.getTokenValue(), null);
    }

    private String encodedRedirect() {
        return URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8);
    }

    private static String validateRedirect(String value) {
        try {
            URI uri = URI.create(value);
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && "/auth".equals(uri.getPath())
                    && uri.getQuery() == null && uri.getFragment() == null) return value;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalArgumentException("STOCK_AUTH_REDIRECT_URL deve apontar para /auth.");
    }

    private static String email(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 254
                || !value.contains("@")) throw bad("E-mail inválido.");
        return value.trim();
    }

    private static String password(String value) {
        if (value == null || value.isEmpty() || value.length() > 1024) {
            throw bad("Senha inválida.");
        }
        return value;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record Credentials(String email, String password) { }
    public record Registration(String email, String password, String displayName) { }
    public record Refresh(String refreshToken) { }
    public record Recovery(String email) { }
    public record NewPassword(String password) { }
}
