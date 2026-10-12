package com.acme.tenancy.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "tenancies")
public class Tenancy {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 80) private String slug;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected Tenancy() {}
    public Tenancy(String name, String slug) { rename(name); this.slug = slug.trim().toLowerCase(); }
    public void rename(String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("Tenancy name is required"); this.name = name.trim(); }
    public UUID getId() { return id; } public String getName() { return name; } public String getSlug() { return slug; } public Instant getCreatedAt() { return createdAt; }
}
