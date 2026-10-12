package com.acme.tenancy.security;
import java.util.UUID;
public record CurrentUser(UUID id, boolean superadmin) {}
