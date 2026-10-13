package com.acme.tenancy.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** RFC 9728 Protected Resource Metadata used by OAuth-capable MCP clients. */
@RestController
@Profile({"production", "oauth-local"})
public class McpProtectedResourceMetadataController {

    private final String resourceUri;
    private final String issuerUri;

    public McpProtectedResourceMetadataController(
            @Value("${app.mcp.resource-uri}") String resourceUri,
            @Value("${app.mcp.issuer-uri}") String issuerUri) {
        this.resourceUri = resourceUri;
        this.issuerUri = issuerUri;
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    public ResponseEntity<Map<String, Object>> metadata() {
        Map<String, Object> body = Map.of(
                "resource", resourceUri,
                "authorization_servers", List.of(issuerUri),
                "scopes_supported", List.of("mcp:connect"),
                "bearer_methods_supported", List.of("header"),
                "resource_name", "Tenancy Platform MCP");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic()).body(body);
    }
}
