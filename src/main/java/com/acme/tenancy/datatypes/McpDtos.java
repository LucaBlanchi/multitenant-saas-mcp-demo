package com.acme.tenancy.datatypes;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

/** Object envelopes keep structured tool results compatible with 2025 MCP clients. */
public final class McpDtos {
    private McpDtos() {}

    public record AccountResult(UUID id, String email, String displayName) {}

    public record TenanciesResult(List<ApiDtos.TenancyView> tenancies) {
        public TenanciesResult {
            tenancies = List.copyOf(tenancies);
        }
    }

    public record WorkItemsResult(List<WorkItemResult> items) {
        public WorkItemsResult {
            items = List.copyOf(items);
        }
    }

    /** An absent description is rendered as empty text in the MCP wire representation. */
    public record WorkItemResult(UUID id, UUID tenancyId, String title, String description,
                                 String status, Instant createdAt, Instant updatedAt) {
        public WorkItemResult {
            description = description == null ? "" : description;
        }

        public static WorkItemResult from(ApiDtos.WorkItemView item) {
            return new WorkItemResult(item.id(), item.tenancyId(), item.title(), item.description(),
                    item.status(), item.createdAt(), item.updatedAt());
        }
    }
}
