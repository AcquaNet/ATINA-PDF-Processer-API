package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.AttachmentProcessingStatus;
import com.atina.invoice.api.model.enums.StorageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Attachment procesado
 * Registra cada archivo adjunto que se procesa
 */
@Entity
@Table(name = "processed_attachments", indexes = {
        @Index(name = "idx_processing_status", columnList = "processing_status"),
        @Index(name = "idx_original_filename", columnList = "original_filename")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email del que proviene este attachment
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_email_id", nullable = false)
    private ProcessedEmail processedEmail;

    /**
     * Regla que matcheó este attachment (null si no matcheó ninguna)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AttachmentProcessingRule rule;

    // ========== Metadata del Archivo ==========

    /**
     * Nombre original del archivo
     */
    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    /**
     * Nombre normalizado del archivo
     * Formato: {senderId}_{emailId}_{sequence}_{source}_{destination}_{timestamp}.ext
     * Ejemplo: 92455890_3_0001_invoice_jde_2026-01-19-18-45-00.pdf
     */
    @Column(name = "normalized_filename", length = 500)
    private String normalizedFilename;

    /**
     * MIME type del archivo
     */
    @Column(name = "mime_type", length = 255)
    private String mimeType;

    /**
     * Tamaño del archivo en bytes
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // ========== Storage ==========

    /**
     * Path del archivo almacenado
     * Local: /private/tmp/process-mails/ACME/process/inbounds/...
     * S3: s3://bucket/ACME/process/inbounds/...
     */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /**
     * Tipo de almacenamiento usado
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 10)
    @Builder.Default
    private StorageType storageType = StorageType.LOCAL;

    // ========== Estado de Procesamiento ==========

    /**
     * Estado del procesamiento
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    @Builder.Default
    private AttachmentProcessingStatus processingStatus = AttachmentProcessingStatus.PENDING;

    /**
     * Mensaje de error (si falló)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ========== Referencia a Job de Extracción ==========

    /**
     * ID del job de extracción (si se creó)
     */
    @Column(name = "extraction_job_id")
    private Long extractionJobId;

    // ========== Metadata ==========

    /**
     * Fecha de creación
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Fecha de última actualización
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ========== Helper Methods ==========

    /**
     * Marcar como descargado
     */
    public void markAsDownloaded(String filePath, Long fileSize) {
        this.processingStatus = AttachmentProcessingStatus.DOWNLOADED;
        this.filePath = filePath;
        this.fileSizeBytes = fileSize;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como en extracción
     */
    public void markAsExtracting(Long jobId) {
        this.processingStatus = AttachmentProcessingStatus.EXTRACTING;
        this.extractionJobId = jobId;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como completado
     */
    public void markAsCompleted() {
        this.processingStatus = AttachmentProcessingStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como fallido
     */
    public void markAsFailed(String errorMessage) {
        this.processingStatus = AttachmentProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como ignorado
     */
    public void markAsIgnored() {
        this.processingStatus = AttachmentProcessingStatus.IGNORED;
        this.updatedAt = Instant.now();
    }

    /**
     * Verificar si matcheó alguna regla
     */
    public boolean hasMatchedRule() {
        return this.rule != null;
    }
}
