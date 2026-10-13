package com.acme.tenancy.services;

import com.acme.tenancy.datatypes.ApiDtos.WorkItemCreate;
import com.acme.tenancy.datatypes.ApiDtos.WorkItemRequest;
import com.acme.tenancy.datatypes.ApiDtos.WorkItemView;
import com.acme.tenancy.entities.MembershipRole;
import com.acme.tenancy.entities.WorkItem;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Shared by REST and MCP: neither transport can bypass the tenant/role policy. */
@Service
public class WorkItemService {
    private final WorkItemRepository items;
    private final TenancyAccessService access;

    public WorkItemService(WorkItemRepository items, TenancyAccessService access) {
        this.items = items;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<WorkItemView> list(UUID user, String tenancyId) {
        var tenancy = access.tenancy(user, tenancyId);
        return items.findAllByTenancyIdOrderByCreatedAtDesc(tenancy.getId()).stream().map(this::view).toList();
    }

    @Transactional
    public WorkItemView create(UUID user, String tenancyId, WorkItemCreate request) {
        var tenancy = access.tenancy(user, tenancyId);
        access.requireRole(user, tenancy.getId(), MembershipRole.ADMIN);
        return view(items.save(new WorkItem(tenancy, request.title(), request.description())));
    }

    @Transactional
    public WorkItemView update(UUID user, String tenancyId, UUID id, WorkItemRequest request) {
        var tenancy = access.tenancy(user, tenancyId);
        access.requireRole(user, tenancy.getId(), MembershipRole.ADMIN);
        var item = scopedItem(id, tenancy.getId());
        item.update(request.title(), request.description(), request.status());
        return view(item);
    }

    @Transactional
    public void delete(UUID user, String tenancyId, UUID id) {
        var tenancy = access.tenancy(user, tenancyId);
        access.requireRole(user, tenancy.getId(), MembershipRole.ADMIN);
        items.delete(scopedItem(id, tenancy.getId()));
    }

    private WorkItem scopedItem(UUID id, UUID tenancyId) {
        return items.findByIdAndTenancyId(id, tenancyId)
                .orElseThrow(() -> ApiException.notFound("Work item not found"));
    }

    public WorkItemView view(WorkItem item) {
        return new WorkItemView(item.getId(), item.getTenancy().getId(), item.getTitle(), item.getDescription(),
                item.getStatus(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
