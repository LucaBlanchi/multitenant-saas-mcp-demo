package com.acme.tenancy.services;
import com.acme.tenancy.entities.*;
import com.acme.tenancy.datatypes.ApiDtos.TenancyView;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
@Service
public class TenancyAccessService {
 private final MembershipRepository memberships; private final TenancyRepository tenancies; private final UserRepository users; private final WorkItemRepository workItems;
 public TenancyAccessService(MembershipRepository m, TenancyRepository t, UserRepository u, WorkItemRepository workItems) { memberships=m; tenancies=t; users=u; this.workItems=workItems; }
 public List<TenancyView> tenancies(UUID userId) { return memberships.findByUserId(userId).stream().map(m -> new TenancyView(m.getTenancy().getId(),m.getTenancy().getName(),m.getTenancy().getSlug(),m.getRole().name(),workItems.countByTenancyId(m.getTenancy().getId()))).toList(); }
 public Tenancy tenancy(UUID userId, String header) { if(header==null||header.isBlank()) throw ApiException.badRequest("X-Tenant-Id header is required"); UUID id; try { id=UUID.fromString(header); } catch(Exception e) { throw ApiException.badRequest("X-Tenant-Id must be a UUID"); } if(!memberships.existsByUserIdAndTenancyId(userId,id)) throw ApiException.forbidden("User is not a member of this tenancy"); return tenancies.findById(id).orElseThrow(()->ApiException.notFound("Tenancy not found")); }
 public Membership requireRole(UUID userId, UUID tenancyId, MembershipRole minimum) { var m=memberships.findByUserIdAndTenancyId(userId,tenancyId).orElseThrow(()->ApiException.forbidden("User is not a member of this tenancy")); if(minimum==MembershipRole.ADMIN && m.getRole()!=MembershipRole.ADMIN) throw ApiException.forbidden("ADMIN membership is required"); return m; }
 public UserAccount user(UUID id) { return users.findById(id).orElseThrow(()->ApiException.unauthorized("User no longer exists")); }
}
