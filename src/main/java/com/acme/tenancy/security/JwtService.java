package com.acme.tenancy.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Signed development REST tokens; production/MCP resource servers never use this parser. */
@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String create(UUID id) {
        Instant now = Instant.now();
        return Jwts.builder().subject(id.toString()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact();
    }

    public UUID parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) throw new IllegalArgumentException("Token expiry required");
        UUID id = UUID.fromString(claims.getSubject());
        if (!id.toString().equalsIgnoreCase(claims.getSubject())) {
            throw new IllegalArgumentException("Token subject must be a UUID");
        }
        return id;
    }
}
