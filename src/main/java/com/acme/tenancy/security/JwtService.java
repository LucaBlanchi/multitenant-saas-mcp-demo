package com.acme.tenancy.security;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class JwtService {
 private final SecretKey key;
 public JwtService(@Value("${app.jwt.secret}") String secret) { if(secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes"); key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
 public String create(UUID id, boolean superadmin) { Instant now=Instant.now(); return Jwts.builder().subject(id.toString()).claim("superadmin",superadmin).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600))).signWith(key).compact(); }
 public CurrentUser parse(String token) { var c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); return new CurrentUser(UUID.fromString(c.getSubject()), Boolean.TRUE.equals(c.get("superadmin", Boolean.class))); }
}
