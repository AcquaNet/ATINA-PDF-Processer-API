package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity para jobs de extracción asíncrona
 *
 * Modificado para soportar storage temporal genérico
 */
@Entity
@Table(name = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false, length = 500000)
    private String requestPayload;  // JSON string

    @Column(length = 1000000)
    private String resultPayload;  // JSON string

    @Column
    private String errorMessage;

    @Column
    private Integer progress;  // 0-100

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column
    private Long durationMs;

    /**
     * Storage ID para PDFs (campo original)
     * @deprecated Usar storageId para nuevo código
     */
    @Deprecated
    @Column(name = "pdf_storage_id")
    private String pdfStorageId;

    /**
     * Storage ID genérico para inputs temporales
     * Nuevo campo para async unificado
     */
    @Column(name = "storage_id")
    private String storageId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = JobStatus.PENDING;
        }
        if (progress == null) {
            progress = 0;
        }
    }
}
