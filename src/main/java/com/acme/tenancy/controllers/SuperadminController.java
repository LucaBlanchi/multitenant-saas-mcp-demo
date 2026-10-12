package com.acme.tenancy.controllers;
import com.acme.tenancy.datatypes.ApiDtos.*;
import com.acme.tenancy.entities.*;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/superadmin")
public class SuperadminController {
 private final TenancyRepository tenancies; private final UserRepository users; private final MembershipRepository memberships; private final PasswordEncoder encoder;
 public SuperadminController(TenancyRepository t,UserRepository u,MembershipRepository m,PasswordEncoder e) { tenancies=t; users=u; memberships=m; encoder=e; }
 private void check(Authentication a) { if(!PrincipalHelper.isSuperadmin(a)) throw ApiException.forbidden("Superadmin access required"); }
 @PostMapping("/tenancies") public Map<String,Object> createTenancy(Authentication a,@Valid @RequestBody CreateTenancyRequest r) { check(a); if(tenancies.findBySlug(r.slug()).isPresent()) throw ApiException.conflict("Slug already exists"); var t=tenancies.save(new Tenancy(r.name(),r.slug())); return Map.of("id",t.getId(),"name",t.getName(),"slug",t.getSlug()); }
 @GetMapping("/tenancies") public List<Map<String,Object>> listTenancies(Authentication a) { check(a); return tenancies.findAllByOrderByNameAsc().stream().map(t->Map.<String,Object>of("id",t.getId(),"name",t.getName(),"slug",t.getSlug())).toList(); }
 @PostMapping("/users") public UserView createUser(Authentication a,@Valid @RequestBody CreateUserRequest r) { check(a); String email=r.email().trim().toLowerCase(); if(users.existsByEmail(email)) throw ApiException.conflict("Email already exists"); var u=users.save(new UserAccount(email,r.displayName(),encoder.encode(r.password()),r.superadmin())); return new UserView(u.getId(),u.getEmail(),u.getDisplayName(),u.isSuperadmin()); }
 @PostMapping("/memberships") public MembershipView createMembership(Authentication a,@Valid @RequestBody CreateMembershipRequest r) { check(a); var u=users.findById(r.userId()).orElseThrow(()->ApiException.notFound("User not found")); var t=tenancies.findById(r.tenancyId()).orElseThrow(()->ApiException.notFound("Tenancy not found")); if(memberships.existsByUserIdAndTenancyId(u.getId(),t.getId())) throw ApiException.conflict("Membership already exists"); var m=memberships.save(new Membership(u,t,r.role())); return new MembershipView(m.getId(),u.getId(),t.getId(),m.getRole().name()); }
 @GetMapping("/users") public List<UserView> listUsers(Authentication a) { check(a); return users.findAll().stream().map(u->new UserView(u.getId(),u.getEmail(),u.getDisplayName(),u.isSuperadmin())).toList(); }
 @GetMapping("/memberships") public List<SuperadminMembershipView> listMemberships(Authentication a) {
  check(a);
  return memberships.findAllForSuperadmin().stream().map(m -> new SuperadminMembershipView(
   m.getId(), m.getUser().getId(), m.getUser().getEmail(), m.getUser().getDisplayName(),
   m.getTenancy().getId(), m.getTenancy().getName(), m.getTenancy().getSlug(), m.getRole().name()
  )).toList();
 }
}
