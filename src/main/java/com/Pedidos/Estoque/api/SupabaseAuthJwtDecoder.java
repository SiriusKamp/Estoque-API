package com.Pedidos.Estoque.api;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Remote verification for Supabase projects with legacy HS256 signing. */
final class SupabaseAuthJwtDecoder implements JwtDecoder {
    private final SupabaseGateway supabase;
    private final String issuer;

    SupabaseAuthJwtDecoder(SupabaseGateway supabase, String issuer) {
        this.supabase = supabase;
        this.issuer = issuer;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        final SignedJWT signed;
        final JWTClaimsSet claims;
        try {
            signed = SignedJWT.parse(token);
            claims = signed.getJWTClaimsSet();
        } catch (ParseException | IllegalArgumentException malformed) {
            throw new BadJwtException("Token de sessão inválido.", malformed);
        }

        UUID subject = subject(claims);
        Date expiry = claims.getExpirationTime();
        List<String> audience = claims.getAudience();
        if (!issuer.equals(claims.getIssuer())
                || audience == null || !audience.contains("authenticated")
                || !"authenticated".equals(role(claims))
                || expiry == null || !expiry.toInstant().isAfter(Instant.now())
                || (claims.getNotBeforeTime() != null
                    && claims.getNotBeforeTime().toInstant().isAfter(Instant.now()))) {
            throw new BadJwtException("Token de sessão inválido.");
        }

        final JsonNode user;
        try {
            // Auth /user verifies the signature before returning the user identity.
            user = supabase.auth("GET", "/user", token, null);
        } catch (ResponseStatusException failure) {
            HttpStatusCode status = failure.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new BadJwtException("Token de sessão inválido.", failure);
            }
            throw new JwtException("Validação de sessão indisponível.", failure);
        }
        if (user == null || !subject.toString().equalsIgnoreCase(user.path("id").asText())) {
            throw new BadJwtException("Identidade da sessão inválida.");
        }

        Map<String, Object> verifiedClaims = new HashMap<>(claims.getClaims());
        verifiedClaims.put("aud", claims.getAudience());
        verifiedClaims.put("exp", expiry.toInstant());
        if (claims.getIssueTime() != null) {
            verifiedClaims.put("iat", claims.getIssueTime().toInstant());
        }
        if (claims.getNotBeforeTime() != null) {
            verifiedClaims.put("nbf", claims.getNotBeforeTime().toInstant());
        }
        return Jwt.withTokenValue(token)
            .header("alg", signed.getHeader().getAlgorithm().getName())
            .claims(values -> values.putAll(verifiedClaims))
            .build();
    }

    private static UUID subject(JWTClaimsSet claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (RuntimeException invalid) {
            throw new BadJwtException("Identidade da sessão inválida.", invalid);
        }
    }

    private static String role(JWTClaimsSet claims) {
        try {
            return claims.getStringClaim("role");
        } catch (ParseException invalid) {
            throw new BadJwtException("Tipo de sessão inválido.", invalid);
        }
    }
}
