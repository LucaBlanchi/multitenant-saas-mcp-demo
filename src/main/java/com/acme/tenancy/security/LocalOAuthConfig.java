package com.acme.tenancy.security;

import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.repositories.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local OAuth exercise only. Production uses a separately operated OIDC issuer. */
@Configuration
@Profile("oauth-local & !production")
public class LocalOAuthConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
        http.oauth2AuthorizationServer(server -> {
                    http.securityMatcher(server.getEndpointsMatcher());
                    server.authorizationServerMetadataEndpoint(metadata -> metadata
                            .authorizationServerMetadataCustomizer(builder -> builder
                                    .tokenEndpointAuthenticationMethods(methods -> {
                                        methods.clear();
                                        methods.add("none");
                                        methods.add("private_key_jwt");
                                    })
                                    .claim("client_id_metadata_document_supported", true)
                                    .scope("mcp:connect")));
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpResourceServer(HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> mcpJwtAuthenticationConverter,
            @Value("${app.mcp.resource-uri}") String resourceUri) throws Exception {
        String metadataUri = resourceUri.replaceFirst("/mcp/?$", "/.well-known/oauth-protected-resource/mcp");
        return http.securityMatcher("/mcp")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("SCOPE_mcp:connect"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                            "Bearer resource_metadata=\"" + metadataUri + "\", scope=\"mcp:connect\"");
                }))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(mcpJwtAuthenticationConverter)))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain login(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/.well-known/oauth-protected-resource",
                                "/.well-known/oauth-protected-resource/mcp").permitAll()
                        .anyRequest().denyAll())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService localOAuthUsers(UserRepository users, PasswordEncoder passwords,
            @Value("${app.mcp.public-base-url}") String publicBaseUrl,
            @Value("${app.mcp.oauth-demo-user1-password:}") String firstPassword,
            @Value("${app.mcp.oauth-demo-user2-password:}") String secondPassword) {
        boolean temporaryCredentials = !firstPassword.isBlank() && !secondPassword.isBlank();
        if (publicBaseUrl.startsWith("https://") && !temporaryCredentials) {
            throw new IllegalStateException("Public OAuth demo requires two random temporary login passwords");
        }
        if (firstPassword.isBlank() != secondPassword.isBlank()) {
            throw new IllegalStateException("Set both temporary OAuth demo passwords together");
        }
        String firstHash = temporaryCredentials ? passwords.encode(firstPassword) : null;
        String secondHash = temporaryCredentials ? passwords.encode(secondPassword) : null;
        return username -> {
            UserAccount account = users.findByEmail(username.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
            String passwordHash = account.getPasswordHash();
            if (temporaryCredentials) {
                passwordHash = switch (account.getEmail()) {
                    case "user1@email.com" -> firstHash;
                    case "user2@email.com" -> secondHash;
                    default -> throw new UsernameNotFoundException("Account unavailable in temporary OAuth demo");
                };
            }
            return User.withUsername(account.getEmail()).password(passwordHash).roles("USER").build();
        };
    }

    @Bean
    RegisteredClientRepository localOAuthClient(
            @Value("${app.mcp.oauth-client-id}") String clientId,
            @Value("${app.mcp.oauth-redirect-uri}") String redirectUri) {
        if (clientId.isBlank() || redirectUri.isBlank()) {
            throw new IllegalStateException("Configure MCP_OAUTH_CLIENT_ID and MCP_OAUTH_REDIRECT_URI");
        }
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope("mcp:connect")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(1)).reuseRefreshTokens(false).build())
                .build();
        return new ChatGptMetadataClientRepository(new InMemoryRegisteredClientRepository(client));
    }

    @Bean
    AuthorizationServerSettings localOAuthSettings(@Value("${app.mcp.issuer-uri}") String issuerUri) {
        return AuthorizationServerSettings.builder().issuer(issuerUri).build();
    }

    @Bean
    KeyPair localOAuthKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Could not generate local OAuth signing key", error);
        }
    }

    @Bean
    JWKSource<SecurityContext> localOAuthJwks(KeyPair localOAuthKeyPair) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) localOAuthKeyPair.getPublic())
                .privateKey((RSAPrivateKey) localOAuthKeyPair.getPrivate())
                .keyID(UUID.randomUUID().toString()).build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    JwtDecoder localOAuthDecoder(KeyPair localOAuthKeyPair,
            @Value("${app.mcp.issuer-uri}") String issuerUri,
            @Value("${app.mcp.resource-uri}") String resourceUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) localOAuthKeyPair.getPublic()).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(resourceUri)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong MCP audience", null));
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult first = issuer.validate(jwt);
            OAuth2TokenValidatorResult second = audience.validate(jwt);
            if (first.hasErrors()) return first;
            return second;
        });
        return decoder;
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> localOAuthClaims(UserRepository users,
            @Value("${app.mcp.resource-uri}") String resourceUri) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            String email = context.getPrincipal().getName();
            UserAccount user = users.findByEmail(email.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new IllegalStateException("OAuth user was removed"));
            context.getClaims().claim("user_id", user.getId().toString()).audience(List.of(resourceUri));
        };
    }
}
