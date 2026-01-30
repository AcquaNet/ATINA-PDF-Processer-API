package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.StorageType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type")
    private StorageType storageType = StorageType.LOCAL;

    @Column(name = "storage_base_path")
    private String storageBasePath = "/private/tmp/process-mails";

    @Column(name = "template_base_path")
    private String templateBasePath = "/config/templates";

    /**
     * Whether extraction processing is enabled for this tenant.
     * Works as global AND per-tenant: both global and this flag must be true.
     */
    @Column(name = "extraction_enabled", nullable = false)
    @Builder.Default
    private boolean extractionEnabled = true;

    /**
     * Whether webhook notifications are enabled for this tenant.
     * Works as global AND per-tenant: both global and this flag must be true.
     */
    @Column(name = "webhook_enabled", nullable = false)
    @Builder.Default
    private boolean webhookEnabled = true;

    /**
     * Webhook URL para notificaciones de extracción completada
     * Se llama cuando todas las tareas de extracción de un email se completan
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    // Campos S3 (opcionales)
    @Column(name = "s3_bucket_name")
    private String s3BucketName;

    @Column(name = "s3_region")
    private String s3Region;

    @Column(name = "s3_access_key")
    private String s3AccessKey;

    @Column(name = "s3_secret_key")
    private String s3SecretKey;

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
