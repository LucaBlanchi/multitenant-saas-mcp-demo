package com.acme.tenancy.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restricted CIMD support for the temporary ChatGPT exercise, not a general URL fetcher. */
final class ChatGptMetadataClientRepository implements RegisteredClientRepository {
    private static final Pattern CLIENT_ID = Pattern.compile(
            "https://chatgpt\\.com/oauth/(?:(?<callback>[A-Za-z0-9_-]{6,128})/)?client\\.json");
    private static final String STABLE_REDIRECT = "https://chatgpt.com/connector_platform_oauth_redirect";
    private final RegisteredClientRepository localClient;
    private final ConcurrentHashMap<String, RegisteredClient> clients = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3)).build();

    ChatGptMetadataClientRepository(RegisteredClientRepository localClient) {
        this.localClient = localClient;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException("Client registration is driven by verified metadata");
    }

    @Override
    public RegisteredClient findById(String id) {
        RegisteredClient local = localClient.findById(id);
        if (local != null) return local;
        return clients.values().stream().filter(client -> client.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient local = localClient.findByClientId(clientId);
        if (local != null) return local;
        Matcher match = CLIENT_ID.matcher(clientId);
        if (!match.matches()) return null;
        return clients.computeIfAbsent(clientId, ignored -> loadVerifiedClient(clientId, match.group("callback")));
    }

    private RegisteredClient loadVerifiedClient(String clientId, String callbackId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(clientId))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                if (response.statusCode() != 200) return null;
                byte[] bytes = stream.readNBytes(16_385);
                if (bytes.length > 16_384) return null;
                JsonNode metadata = json.readTree(bytes);
                String expectedRedirect = callbackId == null
                        ? STABLE_REDIRECT : "https://chatgpt.com/connector/oauth/" + callbackId;
                if (!clientId.equals(metadata.path("client_id").asText())
                        || !singleValue(metadata.path("redirect_uris"), expectedRedirect)
                        || !contains(metadata.path("token_endpoint_auth_methods_supported"), "none")
                        || !contains(metadata.path("grant_types"), "authorization_code")
                        || !contains(metadata.path("response_types"), "code")) return null;

                ClientSettings.Builder settings = ClientSettings.builder()
                        .requireProofKey(true).requireAuthorizationConsent(true);
                RegisteredClient.Builder client = RegisteredClient.withId(UUID.nameUUIDFromBytes(clientId.getBytes(StandardCharsets.UTF_8)).toString())
                        .clientId(clientId)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri(expectedRedirect)
                        .scope("mcp:connect")
                        .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofDays(1)).reuseRefreshTokens(false).build());
                if (contains(metadata.path("token_endpoint_auth_methods_supported"), "private_key_jwt")
                        && "https://chatgpt.com/oauth/jwks.json".equals(metadata.path("jwks_uri").asText())
                        && "RS256".equals(metadata.path("token_endpoint_auth_signing_alg").asText())) {
                    client.clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT);
                    settings.jwkSetUrl("https://chatgpt.com/oauth/jwks.json")
                            .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256);
                }
                return client.clientSettings(settings.build()).build();
            }
        } catch (Exception error) {
            // A failed or malformed remote document is an unknown client; no fallback to caller-supplied URLs.
            return null;
        }
    }

    private static boolean contains(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode element : array) if (value.equals(element.asText())) return true;
        return false;
    }

    private static boolean singleValue(JsonNode array, String value) {
        return array.isArray() && array.size() == 1 && value.equals(array.get(0).asText());
    }
}
