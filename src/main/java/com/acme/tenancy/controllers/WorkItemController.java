package com.acme.tenancy.controllers;
import com.acme.tenancy.datatypes.ApiDtos.*;
import com.acme.tenancy.services.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/work-items")
public class WorkItemController {
 private final WorkItemService service; public WorkItemController(WorkItemService s) { service=s; }
 @GetMapping public List<WorkItemView> list(Authentication a,@RequestHeader(value="X-Tenant-Id",required=false) String tenant) { return service.list(PrincipalHelper.id(a),tenant); }
 @PostMapping public WorkItemView create(Authentication a,@RequestHeader(value="X-Tenant-Id",required=false) String tenant,@Valid @RequestBody WorkItemCreate r) { return service.create(PrincipalHelper.id(a),tenant,r); }
 @PutMapping("/{id}") public WorkItemView update(Authentication a,@RequestHeader(value="X-Tenant-Id",required=false) String tenant,@PathVariable UUID id,@Valid @RequestBody WorkItemRequest r) { return service.update(PrincipalHelper.id(a),tenant,id,r); }
 @DeleteMapping("/{id}") public void delete(Authentication a,@RequestHeader(value="X-Tenant-Id",required=false) String tenant,@PathVariable UUID id) { service.delete(PrincipalHelper.id(a),tenant,id); }
}
