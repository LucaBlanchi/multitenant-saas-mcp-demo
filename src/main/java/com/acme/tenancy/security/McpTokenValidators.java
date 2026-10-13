package com.acme.tenancy.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** The same token policy applies to the local OAuth exercise and the external cloud issuer. */
final class McpTokenValidators {
    private McpTokenValidators() {}

    static OAuth2TokenValidator<Jwt> forResource(String issuer, String resource) {
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            if (jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                return invalid("Access token requires exp and sub claims");
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(resource)) {
                return invalid("Access token audience is not this MCP resource");
            }
            return OAuth2TokenValidatorResult.success();
        };
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), requiredClaims);
    }

    private static OAuth2TokenValidatorResult invalid(String message) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
    }
}
