package com.acme.tenancy.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tenancy_id"}))
public class Membership {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "tenancy_id", nullable = false) private Tenancy tenancy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private MembershipRole role;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected Membership() {}
    public Membership(UserAccount user, Tenancy tenancy, MembershipRole role) { this.user = user; this.tenancy = tenancy; this.role = role; }
    public void changeRole(MembershipRole role) { this.role = role; }
    public UUID getId() { return id; } public UserAccount getUser() { return user; } public Tenancy getTenancy() { return tenancy; } public MembershipRole getRole() { return role; }
}
