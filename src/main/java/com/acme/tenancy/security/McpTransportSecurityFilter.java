package com.acme.tenancy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Host/Origin allow-list for Streamable HTTP, including DNS-rebinding protection. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class McpTransportSecurityFilter extends OncePerRequestFilter {

    private final Set<String> allowedHosts;
    private final Set<String> allowedOrigins;
    private final String demoAccessKey;
    private final boolean demoProfile;

    public McpTransportSecurityFilter(
            @Value("${app.mcp.allowed-hosts}") String hosts,
            @Value("${app.mcp.allowed-origins}") String origins,
            @Value("${app.mcp.demo-access-key:}") String demoAccessKey,
            Environment environment) {
        this.allowedHosts = parse(hosts);
        this.allowedOrigins = parse(origins);
        this.demoProfile = environment.acceptsProfiles(Profiles.of("mcp-demo"));
        this.demoAccessKey = demoAccessKey;
        if (demoProfile && environment.acceptsProfiles(Profiles.of("production"))) {
            throw new IllegalStateException("The mcp-demo profile cannot be combined with production");
        }
        if (demoProfile && !StringUtils.hasText(demoAccessKey)) {
            throw new IllegalStateException("Set MCP_DEMO_ACCESS_KEY for the temporary MCP demo endpoint");
        }
        if (environment.acceptsProfiles(Profiles.of("production"))) {
            if (allowedHosts.isEmpty() || allowedHosts.stream().anyMatch(McpTransportSecurityFilter::isLoopback)) {
                throw new IllegalStateException("Set MCP_ALLOWED_HOSTS to the public MCP host(s) in production");
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = normalize(request.getHeader("Host"));
        String origin = request.getHeader("Origin");
        if (host == null || !allowedHosts.contains(host)
                || (demoProfile && !matchesDemoKey(request.getParameter("mcp_demo_key")))
                || (StringUtils.hasText(origin) && !allowedOrigins.contains(origin.trim()))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matchesDemoKey(String supplied) {
        return supplied != null && MessageDigest.isEqual(
                demoAccessKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> parse(String input) {
        if (!StringUtils.hasText(input)) {
            return Set.of();
        }
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(McpTransportSecurityFilter::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isLoopback(String host) {
        return host.equals("localhost") || host.startsWith("localhost:")
                || host.equals("127.0.0.1") || host.startsWith("127.0.0.1:")
                || host.equals("[::1]") || host.startsWith("[::1]:");
    }
}
