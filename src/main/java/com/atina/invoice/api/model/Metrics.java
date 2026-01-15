package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metrics entity - Enhanced with multi-tenancy support
 * Stores application metrics per tenant for usage tracking and billing
 */
@Entity
@Table(name = "metrics",
        indexes = {
                @Index(name = "idx_tenant_metric", columnList = "tenant_id, metric_key"),
                @Index(name = "idx_metric_key", columnList = "metric_key"),
                @Index(name = "idx_created_at", columnList = "created_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant this metric belongs to (null = system-wide metric)
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * Metric key (e.g., "extractions.total", "extractions.success")
     * Combined with tenant_id to form unique tracking key
     */
    @Column(nullable = false, length = 100)
    private String metricKey;

    /**
     * Metric value (counter)
     */
    @Column(nullable = false)
    private Long metricValue;

    /**
     * Optional: metric metadata as JSON string
     * Can store additional context like {operation: "extract", duration_ms: 1234}
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Last updated timestamp
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Created timestamp
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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
