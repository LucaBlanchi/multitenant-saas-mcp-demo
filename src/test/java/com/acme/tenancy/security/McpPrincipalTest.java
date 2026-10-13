package com.acme.tenancy.security;

import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpPrincipalTest {
    @Test void userAndSuperadminAuthorityComeFromDatabaseNotCallerClaims() {
        var users = mock(UserRepository.class);
        var user = mock(UserAccount.class);
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(id);
        when(user.isSuperadmin()).thenReturn(false);
        var token = Jwt.withTokenValue("dummy").header("alg", "RS256").subject("subject")
                .claim("user_id", id.toString()).claim("superadmin", true)
                .claim("roles", java.util.List.of("ADMIN", "SUPERADMIN")).claim("scope", "mcp:connect").build();
        var authentication = new SecurityConfig().mcpJwtAuthenticationConverter(users).convert(token);
        assertEquals(new CurrentUser(id, false), authentication.getPrincipal());
        assertFalse(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN")));
    }
    @Test void deletedAccountCannotAuthenticateWithAnOldValidToken() {
        var users = mock(UserRepository.class);
        var token = Jwt.withTokenValue("dummy").header("alg", "RS256").subject("subject")
                .claim("user_id", UUID.randomUUID().toString()).build();
        assertThrows(InvalidBearerTokenException.class,
                () -> new SecurityConfig().mcpJwtAuthenticationConverter(users).convert(token));
    }
    @Test void missingUserIdFailsClosed() {
        var users = mock(UserRepository.class);
        var token = Jwt.withTokenValue("dummy").header("alg", "RS256").subject("subject").build();
        assertThrows(InvalidBearerTokenException.class,
                () -> new SecurityConfig().mcpJwtAuthenticationConverter(users).convert(token));
        verifyNoInteractions(users);
    }
}
