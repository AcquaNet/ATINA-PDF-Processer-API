package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tenant entity - Represents a company/organization using the system
 * Enables multi-tenancy: multiple companies can use the same API instance
 */
@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique tenant code (e.g., "ACME", "GLOBEX", "INITECH")
     */
    @Column(unique = true, nullable = false, length = 50)
    private String tenantCode;

    /**
     * Tenant display name (e.g., "ACME Corporation")
     */
    @Column(nullable = false, length = 200)
    private String tenantName;

    /**
     * Contact email for tenant
     */
    @Column(length = 200)
    private String contactEmail;

    /**
     * Whether this tenant is active
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Maximum API calls per month (null = unlimited)
     */
    @Column
    private Long maxApiCallsPerMonth;

    /**
     * Maximum storage in MB (null = unlimited)
     */
    @Column
    private Long maxStorageMb;

    /**
     * Tenant subscription tier (FREE, BASIC, PREMIUM, ENTERPRISE)
     */
    @Column(length = 50)
    private String subscriptionTier;

    /**
     * Created timestamp
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Last updated timestamp
     */
    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
