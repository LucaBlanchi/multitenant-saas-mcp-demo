package com.acme.tenancy.controllers;
import com.acme.tenancy.security.CurrentUser;
import org.springframework.security.core.Authentication;
import java.util.UUID;
final class PrincipalHelper { private PrincipalHelper() {} static UUID id(Authentication a) { return ((CurrentUser)a.getPrincipal()).id(); } static boolean isSuperadmin(Authentication a) { return ((CurrentUser)a.getPrincipal()).superadmin(); } }
