package com.acme.tenancy.security;

import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevelopmentJwtFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void roleIsReloadedAndDoesNotStayInAnOldToken() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(UserRepository.class);
        var user = mock(UserAccount.class);
        UUID id = UUID.randomUUID();
        when(jwt.parse("dummy")).thenReturn(id);
        when(user.getId()).thenReturn(id);
        when(user.isSuperadmin()).thenReturn(true, true, false, false);
        when(users.findById(id)).thenReturn(Optional.of(user));
        var filter = new JwtFilter(jwt, users);
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer dummy");
        filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {});
        assertTrue(((CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).superadmin());
        filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {});
        assertFalse(((CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).superadmin());
    }
    @Test void deletedAccountIsNotAuthenticated() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(UserRepository.class);
        when(jwt.parse("dummy")).thenReturn(UUID.randomUUID());
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer dummy");
        new JwtFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), (a,b) -> {});
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
