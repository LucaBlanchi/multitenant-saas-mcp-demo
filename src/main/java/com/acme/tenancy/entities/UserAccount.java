package com.acme.tenancy.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(nullable = false) private String passwordHash;
    @Column(nullable = false) private boolean superadmin = false;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected UserAccount() {}
    public UserAccount(String email, String displayName, String passwordHash, boolean superadmin) { this.email = email.trim().toLowerCase(); this.displayName = displayName.trim(); this.passwordHash = passwordHash; this.superadmin = superadmin; }
    public UUID getId() { return id; } public String getEmail() { return email; } public String getDisplayName() { return displayName; } public String getPasswordHash() { return passwordHash; } public boolean isSuperadmin() { return superadmin; } public Instant getCreatedAt() { return createdAt; }
}
