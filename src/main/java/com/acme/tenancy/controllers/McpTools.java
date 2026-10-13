package com.acme.tenancy.controllers;

import com.acme.tenancy.datatypes.ApiDtos.WorkItemCreate;
import com.acme.tenancy.datatypes.ApiDtos.WorkItemRequest;
import com.acme.tenancy.datatypes.McpDtos.TenanciesResult;
import com.acme.tenancy.datatypes.McpDtos.WorkItemsResult;
import com.acme.tenancy.datatypes.McpDtos.WorkItemResult;
import com.acme.tenancy.datatypes.McpDtos.AccountResult;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.UserRepository;
import com.acme.tenancy.services.McpAuditService;
import com.acme.tenancy.services.TenancyAccessService;
import com.acme.tenancy.services.WorkItemService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class McpTools {

    private final WorkItemService workItems;
    private final TenancyAccessService access;
    private final McpAuditService audit;
    private final UserRepository users;

    public McpTools(WorkItemService workItems, TenancyAccessService access, McpAuditService audit,
                    UserRepository users) {
        this.workItems = workItems;
        this.access = access;
        this.audit = audit;
        this.users = users;
    }

    @McpTool(
            name = "account_me",
            title = "My account",
            description = "Return the authenticated account ID, email and name. Use this to confirm which account the MCP client connected.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public AccountResult account() {
        UUID userId = currentUserId();
        return audited("account_me", userId, null, () -> {
            var account = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
            return new AccountResult(account.getId(), account.getEmail(), account.getDisplayName());
        });
    }

    @McpTool(
            name = "tenancies_list",
            title = "List my organizations",
            description = "List only the organizations and membership roles of the authenticated user, as shown in the dashboard.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public TenanciesResult tenancies() {
        UUID userId = currentUserId();
        return audited("tenancies_list", userId, null,
                () -> new TenanciesResult(access.tenancies(userId)));
    }

    @McpTool(
            name = "work_items_list",
            title = "List work items",
            description = "List work items for a tenancy where the authenticated user has a membership. Read only.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public WorkItemsResult list(
            @McpToolParam(description = "UUID of a tenancy the signed-in user belongs to", required = true) String tenancyId) {
        UUID userId = currentUserId();
        return audited("work_items_list", userId, tenancyId,
                () -> new WorkItemsResult(workItems.list(userId, tenancyId).stream()
                        .map(WorkItemResult::from).toList()));
    }

    @McpTool(
            name = "work_items_create",
            title = "Create work item",
            description = "Create an OPEN work item in a tenancy. The authenticated user must be an ADMIN member of that tenancy.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public WorkItemResult create(
            @McpToolParam(description = "UUID of a tenancy the signed-in user administers", required = true) String tenancyId,
            @McpToolParam(description = "Short work item title (1 to 160 characters)", required = true) String title,
            @McpToolParam(description = "Optional description (up to 2000 characters)", required = false) String description) {
        UUID userId = currentUserId();
        validateTitle(title);
        validateDescription(description);
        return audited("work_items_create", userId, tenancyId,
                () -> WorkItemResult.from(workItems.create(userId, tenancyId,
                        new WorkItemCreate(title.trim(), description))));
    }

    @McpTool(
            name = "work_items_update",
            title = "Update work item",
            description = "Update a work item in a tenancy. The authenticated user must be an ADMIN member of that tenancy.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public WorkItemResult update(
            @McpToolParam(description = "UUID of a tenancy the signed-in user administers", required = true) String tenancyId,
            @McpToolParam(description = "UUID of the work item", required = true) String id,
            @McpToolParam(description = "Short work item title (1 to 160 characters)", required = true) String title,
            @McpToolParam(description = "Description; null clears it", required = false) String description,
            @McpToolParam(description = "New status", required = true) String status) {
        UUID userId = currentUserId();
        UUID workItemId = parseUuid(id, "id");
        validateTitle(title);
        validateDescription(description);
        if (status == null || !List.of("OPEN", "IN_PROGRESS", "DONE").contains(status)) {
            throw ApiException.badRequest("status must be OPEN, IN_PROGRESS, or DONE");
        }
        return audited("work_items_update", userId, tenancyId,
                () -> WorkItemResult.from(workItems.update(userId, tenancyId, workItemId,
                        new WorkItemRequest(title.trim(), description, status))));
    }

    @McpTool(
            name = "work_items_delete",
            title = "Delete work item",
            description = "Permanently delete a work item from a tenancy. The authenticated user must be an ADMIN member.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    public Map<String, Boolean> delete(
            @McpToolParam(description = "UUID of a tenancy the signed-in user administers", required = true) String tenancyId,
            @McpToolParam(description = "UUID of the work item", required = true) String id) {
        UUID userId = currentUserId();
        UUID workItemId = parseUuid(id, "id");
        return audited("work_items_delete", userId, tenancyId, () -> {
            workItems.delete(userId, tenancyId, workItemId);
            return Map.of("deleted", true);
        });
    }

    private UUID currentUserId() {
        return PrincipalHelper.id(SecurityContextHolder.getContext().getAuthentication());
    }

    private <T> T audited(String tool, UUID userId, String tenancyId, Supplier<T> operation) {
        long startedAt = System.nanoTime();
        try {
            T result = operation.get();
            audit.record(tool, userId, tenancyId, "success", startedAt);
            return result;
        }
        catch (RuntimeException exception) {
            audit.record(tool, userId, tenancyId, "failure", startedAt);
            throw exception;
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > 160) {
            throw ApiException.badRequest("title must contain between 1 and 160 characters");
        }
    }

    private static void validateDescription(String description) {
        if (description != null && description.length() > 2000) {
            throw ApiException.badRequest("description must contain at most 2000 characters");
        }
    }

    private static UUID parseUuid(String value, String field) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        }
        catch (RuntimeException exception) {
            throw ApiException.badRequest(field + " must be a UUID");
        }
    }
}
