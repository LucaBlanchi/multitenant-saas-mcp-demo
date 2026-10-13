package com.acme.tenancy.services;

import com.acme.tenancy.datatypes.ApiDtos.TenancyView;
import com.acme.tenancy.entities.Membership;
import com.acme.tenancy.entities.MembershipRole;
import com.acme.tenancy.entities.Tenancy;
import com.acme.tenancy.entities.UserAccount;
import com.acme.tenancy.exceptions.ApiException;
import com.acme.tenancy.repositories.MembershipRepository;
import com.acme.tenancy.repositories.TenancyRepository;
import com.acme.tenancy.repositories.UserRepository;
import com.acme.tenancy.repositories.WorkItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Tenant permissions always come from current database memberships, including for superadmins. */
@Service
@Transactional(readOnly = true)
public class TenancyAccessService {
    private final MembershipRepository memberships;
    private final TenancyRepository tenancies;
    private final UserRepository users;
    private final WorkItemRepository workItems;

    public TenancyAccessService(MembershipRepository memberships, TenancyRepository tenancies,
            UserRepository users, WorkItemRepository workItems) {
        this.memberships = memberships;
        this.tenancies = tenancies;
        this.users = users;
        this.workItems = workItems;
    }

    public List<TenancyView> tenancies(UUID userId) {
        return memberships.findByUserId(userId).stream()
                .map(m -> new TenancyView(m.getTenancy().getId(), m.getTenancy().getName(),
                        m.getTenancy().getSlug(), m.getRole().name(),
                        workItems.countByTenancyId(m.getTenancy().getId())))
                .toList();
    }

    public Tenancy tenancy(UUID userId, String tenancyId) {
        UUID id = parseTenancyId(tenancyId);
        requireMembership(userId, id);
        return tenancies.findById(id).orElseThrow(() -> ApiException.notFound("Tenancy not found"));
    }

    public Membership requireRole(UUID userId, UUID tenancyId, MembershipRole required) {
        Membership membership = requireMembership(userId, tenancyId);
        if (required == MembershipRole.ADMIN && membership.getRole() != MembershipRole.ADMIN) {
            throw ApiException.forbidden("ADMIN membership is required");
        }
        return membership;
    }

    private Membership requireMembership(UUID userId, UUID tenancyId) {
        return memberships.findByUserIdAndTenancyId(userId, tenancyId)
                .orElseThrow(() -> ApiException.forbidden("User is not a member of this tenancy"));
    }

    public UserAccount user(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }

    private static UUID parseTenancyId(String value) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("tenancyId is required");
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("tenancyId must be a UUID");
        }
    }
}
