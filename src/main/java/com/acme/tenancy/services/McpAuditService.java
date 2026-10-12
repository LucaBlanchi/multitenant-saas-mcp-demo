package com.acme.tenancy.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Audit metadata only: arguments and tenant business data are deliberately omitted. */
@Service
public class McpAuditService {

    private static final Logger log = LoggerFactory.getLogger(McpAuditService.class);

    public void record(String tool, UUID userId, String tenancyId, String outcome, long startedAtNanos) {
        long durationMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info("mcp_tool_call tool={} user_id={} tenancy_id={} outcome={} duration_ms={}",
                tool, userId, safeTenancyId(tenancyId), outcome, durationMillis);
    }

    private String safeTenancyId(String value) {
        if (value == null || value.length() > 64 || !value.matches("[A-Fa-f0-9-]+")) {
            return "invalid";
        }
        return value;
    }
}
