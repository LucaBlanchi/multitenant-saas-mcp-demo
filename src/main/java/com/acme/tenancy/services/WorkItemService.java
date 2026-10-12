package com.acme.tenancy.services;
import com.acme.tenancy.datatypes.ApiDtos.*;
import com.acme.tenancy.entities.*;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class WorkItemService {
 private final WorkItemRepository items; private final TenancyAccessService access;
 public WorkItemService(WorkItemRepository items, TenancyAccessService access) { this.items=items; this.access=access; }
 @Transactional(readOnly=true) public List<WorkItemView> list(UUID user, String tenant) { var t=access.tenancy(user,tenant); return items.findAllByTenancyIdOrderByCreatedAtDesc(t.getId()).stream().map(this::view).toList(); }
 @Transactional public WorkItemView create(UUID user, String tenant, WorkItemCreate req) { var t=access.tenancy(user,tenant); access.requireRole(user,t.getId(),MembershipRole.ADMIN); return view(items.save(new WorkItem(t,req.title(),req.description()))); }
 @Transactional public WorkItemView update(UUID user,String tenant,UUID id,WorkItemRequest req) { var t=access.tenancy(user,tenant); access.requireRole(user,t.getId(),MembershipRole.ADMIN); var item=items.findByIdAndTenancyId(id,t.getId()).orElseThrow(()->ApiException.notFound("Work item not found")); item.update(req.title(),req.description(),req.status()); return view(item); }
 @Transactional public void delete(UUID user,String tenant,UUID id) { var t=access.tenancy(user,tenant); access.requireRole(user,t.getId(),MembershipRole.ADMIN); var item=items.findByIdAndTenancyId(id,t.getId()).orElseThrow(()->ApiException.notFound("Work item not found")); items.delete(item); }
 public WorkItemView view(WorkItem i) { return new WorkItemView(i.getId(),i.getTenancy().getId(),i.getTitle(),i.getDescription(),i.getStatus(),i.getCreatedAt(),i.getUpdatedAt()); }
}
