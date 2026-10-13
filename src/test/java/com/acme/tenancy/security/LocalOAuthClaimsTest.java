package com.acme.tenancy.security;

import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalOAuthClaimsTest {
    @Test void tokenAndRefreshIssuanceUseOriginalUuidNotRecycledEmail() {
        var users = mock(UserRepository.class);
        UUID id = UUID.randomUUID();
        var user = mock(UserAccount.class);
        when(user.getId()).thenReturn(id);
        when(users.findById(id)).thenReturn(Optional.of(user));
        var context = context(id);
        var claims = JwtClaimsSet.builder();
        when(context.getClaims()).thenReturn(claims);
        new LocalOAuthConfig().localOAuthClaims(users, "https://mcp.example.test/mcp").customize(context);
        assertEquals(id.toString(), claims.build().getSubject());
        assertEquals(id.toString(), claims.build().getClaim("user_id"));
        assertEquals(List.of("https://mcp.example.test/mcp"), claims.build().getAudience());
        verify(users, never()).findByEmail(anyString());
    }
    @Test void deletedAccountCannotMintTokensFromOldAuthorization() {
        var users = mock(UserRepository.class);
        assertThrows(IllegalStateException.class,
                () -> new LocalOAuthConfig().localOAuthClaims(users, "resource").customize(context(UUID.randomUUID())));
    }
    private JwtEncodingContext context(UUID id) {
        var context = mock(JwtEncodingContext.class);
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getPrincipal()).thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                new LocalOAuthConfig.OAuthUser(id, "recycled@example.test", "unused-dummy-hash"), null, List.of()));
        return context;
    }
}
