package com.acme.tenancy.security;

import com.acme.tenancy.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    /** Local UI/demo login. This signed development token is never accepted in production. */
    @Bean
    @Profile("!production & !oauth-local")
    @Order(1)
    SecurityFilterChain localSecurity(HttpSecurity http, JwtFilter jwt) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/error").permitAll()
                        .requestMatchers("/mcp", "/mcp/**").denyAll()
                        .requestMatchers("/api/superadmin/**").hasRole("SUPERADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication required\"}");
                }))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Cloud profile: accept access tokens only from the configured OAuth authorization server. */
    @Bean
    @Profile("production")
    @Order(1)
    SecurityFilterChain productionSecurity(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> mcpJwtAuthenticationConverter,
            @Value("${app.mcp.resource-uri}") String resourceUri) throws Exception {
        String metadataUri = resourceUri.replaceFirst("/mcp/?$", "/.well-known/oauth-protected-resource/mcp");
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp", "/error").permitAll()
                        .requestMatchers("/api/auth/login").denyAll()
                        .requestMatchers("/api/superadmin/**").access(
                                org.springframework.security.authorization.AuthorizationManagers.allOf(
                                        org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority("SCOPE_api:access"),
                                        org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole("SUPERADMIN")))
                        .requestMatchers("/mcp").hasAuthority("SCOPE_mcp:connect")
                        .requestMatchers("/api/**").hasAuthority("SCOPE_api:access")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer resource_metadata=\"" + metadataUri + "\"");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHENTICATED\",\"message\":\"A valid OAuth access token is required\"}");
                }))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(mcpJwtAuthenticationConverter)))
                .build();
    }

    @Bean
    @Profile("production")
    JwtDecoder mcpJwtDecoder(
            @Value("${app.mcp.issuer-uri}") String issuerUri,
            @Value("${app.mcp.resource-uri}") String resourceUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("MCP_ISSUER_URI is required in the production profile");
        }
        if (!issuerUri.startsWith("https://") || !resourceUri.startsWith("https://")) {
            throw new IllegalStateException("Production MCP issuer and resource URLs must use HTTPS");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(McpTokenValidators.forResource(issuerUri, resourceUri));
        return decoder;
    }

    @Bean
    @Profile({"production", "oauth-local"})
    Converter<Jwt, ? extends AbstractAuthenticationToken> mcpJwtAuthenticationConverter(UserRepository users) {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        return jwt -> {
            String internalUserId = jwt.getClaimAsString("user_id");
            UUID userId;
            try {
                userId = UUID.fromString(internalUserId);
            }
            catch (RuntimeException exception) {
                throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException(
                        "Access token must contain a valid user_id claim");
            }
            var user = users.findById(userId).orElseThrow(() ->
                    new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException("Unknown user"));
            List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.add(new SimpleGrantedAuthority(user.isSuperadmin() ? "ROLE_SUPERADMIN" : "ROLE_USER"));
            return new UsernamePasswordAuthenticationToken(new CurrentUser(user.getId(), user.isSuperadmin()), jwt, authorities);
        };
    }
}
