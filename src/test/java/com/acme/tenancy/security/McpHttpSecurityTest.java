package com.acme.tenancy.security;

import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Real Spring filter chain; no server, external issuer, database, or business writes. */
@SpringJUnitWebConfig(McpHttpSecurityTest.Config.class)
class McpHttpSecurityTest {
    private static final String RESOURCE = "https://mcp.example.test/mcp";
    private static final UUID USER = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder decoder;
    @Autowired UserRepository users;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(decoder, users);
        var user = mock(UserAccount.class);
        when(user.getId()).thenReturn(USER);
        when(user.isSuperadmin()).thenReturn(true);
        when(users.findById(USER)).thenReturn(Optional.of(user));
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void anonymousMcpRequiresOAuthDiscovery() throws Exception {
        mvc.perform(post("/mcp")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("resource_metadata")));
    }
    @Test void invalidBearerIsRejected() throws Exception {
        when(decoder.decode("dummy")).thenThrow(new BadJwtException("Bad signature"));
        mvc.perform(post("/mcp").header("Authorization", "Bearer dummy")).andExpect(status().isUnauthorized());
    }
    @Test void validUserWithoutMcpScopeCannotUseTools() throws Exception {
        token("api:access");
        mvc.perform(post("/mcp").header("Authorization", "Bearer dummy")).andExpect(status().isForbidden());
    }
    @Test void mcpScopeAllowsMcpTransport() throws Exception {
        token("mcp:connect");
        mvc.perform(post("/mcp").header("Authorization", "Bearer dummy")).andExpect(status().isOk());
    }
    @Test void mcpTokenCannotUseRestEvenAsSuperadmin() throws Exception {
        token("mcp:connect");
        mvc.perform(get("/api/superadmin/users").header("Authorization", "Bearer dummy")).andExpect(status().isForbidden());
        mvc.perform(get("/api/work-items").header("Authorization", "Bearer dummy")).andExpect(status().isForbidden());
    }
    @Test void apiScopeWithDatabaseSuperadminAllowsAdministrativeTransport() throws Exception {
        token("api:access");
        mvc.perform(get("/api/superadmin/users").header("Authorization", "Bearer dummy")).andExpect(status().isOk());
    }
    @Test void apiScopeDoesNotElevateANormalUser() throws Exception {
        var user = mock(UserAccount.class);
        when(user.getId()).thenReturn(USER);
        when(users.findById(USER)).thenReturn(Optional.of(user));
        token("api:access");
        mvc.perform(get("/api/superadmin/users").header("Authorization", "Bearer dummy")).andExpect(status().isForbidden());
    }
    @Test void removedUserCannotUseMcp() throws Exception {
        when(users.findById(USER)).thenReturn(Optional.empty());
        token("mcp:connect");
        mvc.perform(post("/mcp").header("Authorization", "Bearer dummy")).andExpect(status().isUnauthorized());
    }
    private void token(String scope) {
        when(decoder.decode("dummy")).thenReturn(Jwt.withTokenValue("dummy").header("alg", "RS256")
                .subject(USER.toString()).claim("user_id", USER.toString()).claim("scope", scope)
                .expiresAt(Instant.now().plusSeconds(300)).build());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean TestEndpoints endpoints() { return new TestEndpoints(); }
        @Bean SecurityFilterChain security(HttpSecurity http, UserRepository users) throws Exception {
            return new SecurityConfig().productionSecurity(http,
                    new SecurityConfig().mcpJwtAuthenticationConverter(users), RESOURCE);
        }
    }
    @RestController static class TestEndpoints {
        @PostMapping("/mcp") String mcp() { return "ok"; }
        @GetMapping("/api/superadmin/users") String admin() { return "ok"; }
        @GetMapping("/api/work-items") String items() { return "ok"; }
    }
}
