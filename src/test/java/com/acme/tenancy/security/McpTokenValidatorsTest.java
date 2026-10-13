package com.acme.tenancy.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class McpTokenValidatorsTest {
    private static final String ISSUER = "https://identity.example.test";
    private static final String RESOURCE = "https://mcp.example.test/mcp";

    @Test void validResourceTokenPasses() { assertFalse(valid(token().build())); }
    @Test void missingExpiryIsRejected() { assertTrue(valid(token().claims(c -> c.remove("exp")).build())); }
    @Test void expiredTokenIsRejected() { assertTrue(valid(token().expiresAt(Instant.now().minusSeconds(120)).build())); }
    @Test void wrongIssuerIsRejected() { assertTrue(valid(token().claim("iss", "https://other.example.test").build())); }
    @Test void wrongAudienceIsRejected() { assertTrue(valid(token().audience(List.of("other-resource")).build())); }
    @Test void absentAudienceIsRejected() { assertTrue(valid(token().claims(c -> c.remove("aud")).build())); }
    @Test void absentSubjectIsRejected() { assertTrue(valid(token().claims(c -> c.remove("sub")).build())); }

    private boolean valid(Jwt jwt) { return McpTokenValidators.forResource(ISSUER, RESOURCE).validate(jwt).hasErrors(); }
    private Jwt.Builder token() {
        return Jwt.withTokenValue("dummy").header("alg", "RS256").claim("iss", ISSUER)
                .subject("dummy-user").audience(List.of(RESOURCE)).expiresAt(Instant.now().plusSeconds(300));
    }
}
