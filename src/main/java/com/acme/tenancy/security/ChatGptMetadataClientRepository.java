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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restricted CIMD support for the temporary ChatGPT exercise, not a general URL fetcher. */
final class ChatGptMetadataClientRepository implements RegisteredClientRepository {
    private static final Pattern CLIENT_ID = Pattern.compile(
            "https://chatgpt\\.com/oauth/(?:(?<callback>[A-Za-z0-9_-]{6,128})/)?client\\.json");
    private static final String STABLE_REDIRECT = "https://chatgpt.com/connector_platform_oauth_redirect";
    private final RegisteredClientRepository localClient;
    private static final int MAX_CLIENTS = 128;
    private static final int MAX_DOCUMENT_BYTES = 16_384;
    private final Map<String, RegisteredClient> clients = new HashMap<>();
    private final Map<String, Instant> failedDocuments = new HashMap<>();
    private final Set<String> loadingDocuments = new HashSet<>();
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
    public synchronized RegisteredClient findById(String id) {
        RegisteredClient local = localClient.findById(id);
        if (local != null) return local;
        return clients.values().stream().filter(client -> client.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null) return null;
        RegisteredClient local = localClient.findByClientId(clientId);
        if (local != null) return local;
        if (!CLIENT_ID.matcher(clientId).matches()) return null;
        synchronized (this) {
            RegisteredClient cached = clients.get(clientId);
            if (cached != null) return cached;
            Instant now = Instant.now();
            failedDocuments.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
            if (clients.size() + loadingDocuments.size() >= MAX_CLIENTS
                    || failedDocuments.containsKey(clientId) || loadingDocuments.size() >= 4
                    || !loadingDocuments.add(clientId)) return null;
        }
        // Never hold the shared repository monitor during external I/O: known clients remain independent.
        RegisteredClient client = null;
        try {
            client = loadVerifiedClient(clientId);
            return client;
        } finally {
            synchronized (this) {
                loadingDocuments.remove(clientId);
                if (client != null) clients.put(clientId, client);
                else if (failedDocuments.size() < MAX_CLIENTS) {
                    failedDocuments.put(clientId, Instant.now().plusSeconds(10));
                }
            }
        }
    }

    private RegisteredClient loadVerifiedClient(String clientId) {
        var request = HttpRequest.newBuilder(URI.create(clientId))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
        var pending = http.sendAsync(request, info -> new BoundedBodySubscriber(MAX_DOCUMENT_BYTES));
        try {
            var response = pending.orTimeout(5, TimeUnit.SECONDS).join();
            if (response.statusCode() != 200) return null;
            return verifiedClient(clientId, json.readTree(response.body()));
        } catch (RuntimeException error) {
            pending.cancel(true);
            return null;
        }
    }

    static RegisteredClient verifiedClient(String clientId, JsonNode metadata) {
        Matcher match = CLIENT_ID.matcher(clientId);
        if (!match.matches()) return null;
        String callbackId = match.group("callback");
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

    /** Cancel before an oversized metadata body can be accumulated; the total async request has a deadline. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private Flow.Subscription subscription;
        private long received;

        BoundedBodySubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > limit) {
                subscription.cancel();
                delegate.onError(new IllegalArgumentException("Client metadata exceeds size limit"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
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
