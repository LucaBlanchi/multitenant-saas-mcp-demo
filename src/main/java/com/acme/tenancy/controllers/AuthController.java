package com.acme.tenancy.controllers;
import com.acme.tenancy.datatypes.ApiDtos.*;
import com.acme.tenancy.services.AuthService;
import com.acme.tenancy.services.TenancyAccessService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth; private final TenancyAccessService access;
 public AuthController(AuthService a, TenancyAccessService ac) { auth=a; access=ac; }
 @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
 @GetMapping("/me") public UserView me(Authentication a) { return auth.view(access.user(PrincipalHelper.id(a))); }
 @GetMapping("/tenancies") public List<TenancyView> tenancies(Authentication a) { return access.tenancies(PrincipalHelper.id(a)); }
}
