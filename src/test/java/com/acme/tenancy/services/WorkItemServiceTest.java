package com.acme.tenancy.services;

import com.acme.tenancy.datatypes.ApiDtos.WorkItemCreate;
import com.acme.tenancy.entities.MembershipRole;
import com.acme.tenancy.entities.Tenancy;
import com.acme.tenancy.entities.WorkItem;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTest {
    @Mock private WorkItemRepository items;
    @Mock private TenancyAccessService access;
    @Mock private Tenancy tenancy;
    @InjectMocks private WorkItemService service;

    @Test
    void createRequiresAdminMembership() {
        UUID userId = UUID.randomUUID();
        UUID tenancyId = UUID.randomUUID();
        when(tenancy.getId()).thenReturn(tenancyId);
        when(access.tenancy(userId, tenancyId.toString())).thenReturn(tenancy);
        doThrow(ApiException.forbidden("ADMIN membership is required"))
                .when(access).requireRole(userId, tenancyId, MembershipRole.ADMIN);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(userId, tenancyId.toString(), new WorkItemCreate("Report", null)));

        assertEquals(403, error.getStatus().value());
        verify(items, never()).save(any(WorkItem.class));
    }

    @Test
    void updateLooksUpItemWithinSelectedTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenancyId = UUID.randomUUID();
        UUID otherTenantItemId = UUID.randomUUID();
        when(tenancy.getId()).thenReturn(tenancyId);
        when(access.tenancy(userId, tenancyId.toString())).thenReturn(tenancy);
        when(items.findByIdAndTenancyId(otherTenantItemId, tenancyId)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> service.update(userId, tenancyId.toString(), otherTenantItemId,
                        new com.acme.tenancy.datatypes.ApiDtos.WorkItemRequest("Changed", null, "DONE")));

        assertEquals(404, error.getStatus().value());
        verify(items).findByIdAndTenancyId(otherTenantItemId, tenancyId);
    }
}
