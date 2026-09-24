package com.Pedidos.Estoque.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(name = "stock.jwt.mode", havingValue = "remote")
    JwtDecoder supabaseAuthJwtDecoder(SupabaseGateway supabase,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new SupabaseAuthJwtDecoder(supabase, issuer);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        BearerTokenAuthenticationEntryPoint invalidToken = new BearerTokenAuthenticationEntryPoint();
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/Login",
                    "/api/v1/auth/register", "/api/v1/auth/cadastro",
                    "/api/v1/auth/refresh", "/api/v1/auth/recover").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/integrations/stocks/*/decrements").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    try {
                        chain.doFilter(request, response);
                    } catch (AuthenticationServiceException unavailable) {
                        response.sendError(503, "Validação de sessão indisponível.");
                    }
                }
            }, BearerTokenAuthenticationFilter.class)
            .oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint((request, response, failure) -> {
                    if (failure instanceof AuthenticationServiceException) {
                        response.sendError(503, "Validação de sessão indisponível.");
                    } else {
                        invalidToken.commence(request, response, failure);
                    }
                })
                .jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${stock.api.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim)
            .filter(value -> !value.isEmpty()).toList());
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
            HttpMethod.PUT.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
