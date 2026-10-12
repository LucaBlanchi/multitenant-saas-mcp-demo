package com.acme.tenancy.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "work_items", indexes = @Index(name = "idx_work_item_tenancy", columnList = "tenancy_id"))
public class WorkItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "tenancy_id", nullable = false) private Tenancy tenancy;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, length = 24) private String status = "OPEN";
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();
    protected WorkItem() {}
    public WorkItem(Tenancy tenancy, String title, String description) { this.tenancy = tenancy; update(title, description, "OPEN"); }
    public void update(String title, String description, String status) {
        if (title == null || title.isBlank() || title.length() > 160) throw new IllegalArgumentException("Title must be between 1 and 160 characters");
        if (description != null && description.length() > 2000) throw new IllegalArgumentException("Description must be at most 2000 characters");
        if (!java.util.Set.of("OPEN", "IN_PROGRESS", "DONE").contains(status)) throw new IllegalArgumentException("Status must be OPEN, IN_PROGRESS, or DONE");
        this.title = title.trim(); this.description = description; this.status = status; this.updatedAt = Instant.now();
    }
    public UUID getId() { return id; } public Tenancy getTenancy() { return tenancy; } public String getTitle() { return title; } public String getDescription() { return description; } public String getStatus() { return status; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
