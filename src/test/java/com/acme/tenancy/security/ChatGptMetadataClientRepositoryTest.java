package com.acme.tenancy.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import static org.junit.jupiter.api.Assertions.*;

class ChatGptMetadataClientRepositoryTest {
    private static final String CLIENT = "https://chatgpt.com/oauth/abcdef123456/client.json";
    private static final String CALLBACK = "https://chatgpt.com/connector/oauth/abcdef123456";
    private final ObjectMapper json = new ObjectMapper();

    @Test void validChatGptClientRequiresPkceConsentAndExactCallback() {
        var client = ChatGptMetadataClientRepository.verifiedClient(CLIENT, metadata());
        assertNotNull(client);
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertTrue(client.getClientSettings().isRequireAuthorizationConsent());
        assertEquals(java.util.Set.of(CALLBACK), client.getRedirectUris());
        assertTrue(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.PRIVATE_KEY_JWT));
        assertEquals("https://chatgpt.com/oauth/jwks.json", client.getClientSettings().getJwkSetUrl());
    }
    @Test void mismatchedDocumentIdentityIsRejected() {
        assertNull(ChatGptMetadataClientRepository.verifiedClient(CLIENT, metadata().put("client_id", "https://evil.test/client.json")));
    }
    @Test void callbackForAnotherConnectionIsRejected() {
        var document = metadata();
        document.putArray("redirect_uris").add("https://chatgpt.com/connector/oauth/other-account");
        assertNull(ChatGptMetadataClientRepository.verifiedClient(CLIENT, document));
    }
    @Test void untrustedJwksCannotEnableSignedClientAuthentication() {
        var client = ChatGptMetadataClientRepository.verifiedClient(CLIENT,
                metadata().put("jwks_uri", "http://127.0.0.1/private"));
        assertNotNull(client);
        assertFalse(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.PRIVATE_KEY_JWT));
    }
    @ParameterizedTest @ValueSource(strings = {
            "http://chatgpt.com/oauth/abcdef123456/client.json",
            "https://chatgpt.com.evil.test/oauth/abcdef123456/client.json",
            "https://chatgpt.com@evil.test/oauth/client.json",
            "https://chatgpt.com/oauth/abcdef123456/client.json?target=private",
            "https://chatgpt.com/oauth/abcdef123456/client.json#private",
            "https://127.0.0.1/oauth/client.json"
    }) void untrustedDocumentUrlsAreRejectedBeforeFetch(String id) {
        assertNull(ChatGptMetadataClientRepository.verifiedClient(id, metadata()));
    }
    private ObjectNode metadata() {
        var doc = json.createObjectNode().put("client_id", CLIENT)
                .put("jwks_uri", "https://chatgpt.com/oauth/jwks.json")
                .put("token_endpoint_auth_signing_alg", "RS256");
        doc.putArray("redirect_uris").add(CALLBACK);
        doc.putArray("grant_types").add("authorization_code").add("refresh_token");
        doc.putArray("response_types").add("code");
        doc.putArray("token_endpoint_auth_methods_supported").add("none").add("private_key_jwt");
        return doc;
    }
}
