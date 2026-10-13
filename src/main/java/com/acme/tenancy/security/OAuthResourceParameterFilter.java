package com.acme.tenancy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** This authorization server issues tokens for exactly one resource; reject other explicit targets. */
@Component
@Profile("oauth-local & !production")
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class OAuthResourceParameterFilter extends OncePerRequestFilter {
    private final String resource;

    public OAuthResourceParameterFilter(@Value("${app.mcp.resource-uri}") String resource) {
        this.resource = resource;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/oauth2/authorize")
                && !request.getRequestURI().equals("/oauth2/token");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String[] targets = request.getParameterValues("resource");
        // Consent submissions and older predefined clients can omit it: their only audience is still this resource.
        if (targets != null && (targets.length != 1 || !resource.equals(targets[0]))) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"error\":\"invalid_target\",\"error_description\":\"Unknown MCP resource\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
