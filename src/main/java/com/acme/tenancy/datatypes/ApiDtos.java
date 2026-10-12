package com.acme.tenancy.datatypes;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class ApiDtos {
 private ApiDtos() {}
 public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
 public record UserView(UUID id, String email, String displayName, boolean superadmin) {}
 public record LoginResponse(String token, UserView user) {}
 public record TenancyView(UUID id, String name, String slug, String role, long workItemCount) {}
 public record WorkItemRequest(@NotBlank @Size(max=160) String title, @Size(max=2000) String description, @NotBlank String status) {}
 public record WorkItemCreate(@NotBlank @Size(max=160) String title, @Size(max=2000) String description) {}
 public record WorkItemView(UUID id, UUID tenancyId, String title, String description, String status, Instant createdAt, Instant updatedAt) {}
 public record CreateTenancyRequest(@NotBlank @Size(max=120) String name, @NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=80) String slug) {}
 public record CreateUserRequest(@Email @NotBlank String email, @NotBlank @Size(max=120) String displayName, @NotBlank @Size(min=12) String password, boolean superadmin) {}
 public record CreateMembershipRequest(@NotNull UUID userId, @NotNull UUID tenancyId, @NotNull com.acme.tenancy.entities.MembershipRole role) {}
 public record MembershipView(UUID id, UUID userId, UUID tenancyId, String role) {}
 public record SuperadminMembershipView(UUID id, UUID userId, String userEmail, String displayName,
                                        UUID tenancyId, String tenancyName, String tenancySlug, String role) {}
}
