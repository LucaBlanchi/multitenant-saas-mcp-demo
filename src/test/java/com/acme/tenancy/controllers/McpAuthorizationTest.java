package com.acme.tenancy.controllers;

import com.acme.tenancy.entities.*;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.*;
import com.acme.tenancy.security.CurrentUser;
import com.acme.tenancy.services.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual MCP -> service -> membership/scoped repository path, with independent dummy users/tenants. */
class McpAuthorizationTest {
    private final UUID user = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID foreignItemId = UUID.randomUUID();
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final TenancyRepository tenancies = mock(TenancyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WorkItemRepository items = mock(WorkItemRepository.class);
    private final Tenancy tenant = mock(Tenancy.class);
    private McpTools tools;

    @BeforeEach void setup() {
        var access = new TenancyAccessService(memberships, tenancies, users, items);
        tools = new McpTools(new WorkItemService(items, access), access, mock(McpAuditService.class), users);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenancies.findById(tenantId)).thenReturn(Optional.of(tenant));
        login(user, false);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"list", "create", "update", "delete"})
    void nonMemberCannotReadOrWrite(String operation) {
        when(memberships.findByUserIdAndTenancyId(user, tenantId)).thenReturn(Optional.empty());
        denied(operation, 403);
        verifyNoInteractions(items);
    }

    @ParameterizedTest @ValueSource(strings = {"create", "update", "delete"})
    void memberCannotWrite(String operation) {
        membership(user, MembershipRole.MEMBER);
        denied(operation, 403);
        verifyNoInteractions(items);
    }

    @Test void memberCanReadOnlySelectedTenant() {
        membership(user, MembershipRole.MEMBER);
        when(items.findAllByTenancyIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        assertTrue(tools.list(tenantId.toString()).items().isEmpty());
        verify(items).findAllByTenancyIdOrderByCreatedAtDesc(tenantId);
    }

    @Test void adminCanCreateInItsOwnTenant() {
        membership(user, MembershipRole.ADMIN);
        when(items.save(any(WorkItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var created = tools.create(tenantId.toString(), "Allowed", null);
        assertEquals(tenantId, created.tenancyId());
        assertEquals("Allowed", created.title());
        verify(items).save(any(WorkItem.class));
    }

    @ParameterizedTest @ValueSource(strings = {"update", "delete"})
    void adminCannotAddressAnotherTenantsItem(String operation) {
        membership(user, MembershipRole.ADMIN);
        when(items.findByIdAndTenancyId(foreignItemId, tenantId)).thenReturn(Optional.empty());
        denied(operation, 404);
        verify(items).findByIdAndTenancyId(foreignItemId, tenantId);
        verify(items, never()).delete(any());
        verify(items, never()).save(any());
    }

    @Test void superadminDoesNotBypassTenantMembership() {
        login(user, true);
        denied("create", 403);
        verifyNoInteractions(items);
    }

    @Test void revocationAppliesToExistingAuthenticationOnNextCall() {
        membership(user, MembershipRole.MEMBER);
        tools.list(tenantId.toString());
        when(memberships.findByUserIdAndTenancyId(user, tenantId)).thenReturn(Optional.empty());
        denied("list", 403);
    }

    @Test void secondAccountNeverInheritsFirstAccountsMembership() {
        membership(user, MembershipRole.ADMIN);
        tools.list(tenantId.toString());
        login(otherUser, false);
        denied("create", 403);
        verify(memberships).findByUserIdAndTenancyId(otherUser, tenantId);
        verify(items, never()).save(any());
    }

    @Test void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();
        denied("list", 401);
        verifyNoInteractions(memberships, items);
    }

    @Test void unauthenticatedPrincipalIsRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(new CurrentUser(user, true), null));
        denied("create", 401);
        verifyNoInteractions(memberships, items);
    }

    private void membership(UUID account, MembershipRole role) {
        when(memberships.findByUserIdAndTenancyId(account, tenantId))
                .thenReturn(Optional.of(new Membership(mock(UserAccount.class), tenant, role)));
    }

    private void denied(String operation, int status) {
        ApiException error = assertThrows(ApiException.class, () -> {
            switch (operation) {
                case "list" -> tools.list(tenantId.toString());
                case "create" -> tools.create(tenantId.toString(), "Demo", null);
                case "update" -> tools.update(tenantId.toString(), foreignItemId.toString(), "Demo", null, "DONE");
                case "delete" -> tools.delete(tenantId.toString(), foreignItemId.toString());
                default -> throw new AssertionError(operation);
            }
        });
        assertEquals(status, error.getStatus().value());
    }

    private static void login(UUID account, boolean superadmin) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new CurrentUser(account, superadmin), null, List.of()));
    }
}
