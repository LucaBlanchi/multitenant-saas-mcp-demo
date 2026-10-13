package com.acme.tenancy.controllers;

import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.security.CurrentUser;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/** Both REST and MCP take identity only from the authenticated server-side principal. */
final class PrincipalHelper {
    private PrincipalHelper() {}

    static UUID id(Authentication authentication) { return user(authentication).id(); }
    static boolean isSuperadmin(Authentication authentication) { return user(authentication).superadmin(); }

    private static CurrentUser user(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw ApiException.unauthorized("A valid user access token is required");
        }
        return user;
    }
}
