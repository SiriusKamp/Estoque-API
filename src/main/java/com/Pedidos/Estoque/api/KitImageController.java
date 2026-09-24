package com.Pedidos.Estoque.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Monolith: useKitImages and lib/kitImages.ts; every image request is authenticated. */
@RestController
@RequestMapping("/api/v1/stocks/{stockId}/kit-images")
public class KitImageController {
    private final KitImageService images;

    public KitImageController(KitImageService images) {
        this.images = images;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestParam List<UUID> kitIds) {
        return images.list(actor(jwt), stockId, kitIds);
    }

    @GetMapping(value = "/{kitId}/content", produces = "image/webp")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @PathVariable UUID kitId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/webp"))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
            .body(images.content(actor(jwt), stockId, kitId, jwt.getTokenValue()));
    }

    @PostMapping(consumes = "image/webp")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestBody byte[] image) {
        return Map.of("path", images.upload(actor(jwt), stockId, jwt.getTokenValue(), image));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stockId, @RequestParam String path) {
        images.delete(actor(jwt), stockId, jwt.getTokenValue(), path);
    }

    private static UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade inválida.");
        }
    }
}
