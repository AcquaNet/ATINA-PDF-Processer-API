package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metrics entity
 * Stores application metrics persistently in database
 */
@Entity
@Table(name = "metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Metric key (e.g., "extractions.total", "extractions.success")
     */
    @Column(nullable = false, unique = true, length = 100)
    private String metricKey;

    /**
     * Metric value
     */
    @Column(nullable = false)
    private Long metricValue;

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
