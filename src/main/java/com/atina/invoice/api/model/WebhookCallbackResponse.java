package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "webhook_callback_responses", indexes = {
        @Index(name = "idx_callback_correlation", columnList = "correlation_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookCallbackResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "task_correlation_id", length = 100)
    private String taskCorrelationId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "reference", length = 500)
    private String reference;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
