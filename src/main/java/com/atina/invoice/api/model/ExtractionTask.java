package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.ExtractionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tarea de extracción de PDF
 *
 * Representa una tarea asíncrona para extraer datos de un PDF
 * procesado desde un email.
 *
 * Flujo:
 * 1. EmailPollingService guarda PDF → crea ProcessedAttachment
 * 2. PdfExtractionService crea ExtractionTask (PENDING)
 * 3. ExtractionWorker procesa task → PROCESSING → COMPLETED/FAILED
 * 4. Resultado se guarda en result_path y raw_result
 */
@Entity
@Table(name = "extraction_tasks",
       indexes = {
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_email", columnList = "processed_email_id"),
           @Index(name = "idx_next_retry", columnList = "next_retry_at"),
           @Index(name = "idx_priority_created", columnList = "priority,created_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Attachment del que se extrae información
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_attachment_id", nullable = false)
    private ProcessedAttachment attachment;

    /**
     * Email al que pertenece el attachment
     * (Redundante pero útil para queries)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_email_id", nullable = false)
    private ProcessedEmail email;

    /**
     * Path al PDF a procesar
     * Copiado de ProcessedAttachment.filePath
     */
    @Column(name = "pdf_path", length = 500, nullable = false)
    private String pdfPath;

    /**
     * Source del documento (copiado de AttachmentProcessingRule.source)
     * Usado para buscar el template correcto
     * Ejemplos: "JDE", "SAP", "invoices"
     */
    @Column(name = "source", length = 50, nullable = false)
    private String source;

    /**
     * Estado de la tarea
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ExtractionStatus status = ExtractionStatus.PENDING;

    /**
     * Prioridad (mayor = más prioritario)
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    /**
     * Path al archivo JSON con el resultado de extracción
     * Ejemplo: /tenant/process/extractions/123_456_extraction.json
     */
    @Column(name = "result_path", length = 500)
    private String resultPath;

    /**
     * Resultado JSON en texto (backup)
     */
    @Column(name = "raw_result", columnDefinition = "TEXT")
    private String rawResult;

    /**
     * Mensaje de error si falló
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Número de intentos realizados
     */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /**
     * Máximo número de intentos
     */
    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    /**
     * Fecha de creación
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Fecha en que comenzó el procesamiento
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Fecha en que completó (exitoso o fallido)
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Fecha del próximo reintento (si está en RETRYING)
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    // ========== Helper Methods ==========

    /**
     * Marcar como en procesamiento
     */
    public void markAsProcessing() {
        this.status = ExtractionStatus.PROCESSING;
        this.startedAt = Instant.now();
        this.attempts = (this.attempts != null ? this.attempts : 0) + 1;
    }

    /**
     * Marcar como completado exitosamente
     */
    public void markAsCompleted(String resultPath, String rawResult) {
        this.status = ExtractionStatus.COMPLETED;
        this.resultPath = resultPath;
        this.rawResult = rawResult;
        this.completedAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * Marcar como fallido (sin más reintentos)
     */
    public void markAsFailed(String errorMessage) {
        this.status = ExtractionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    /**
     * Marcar para reintento
     * Si excede max_attempts, marca como FAILED
     */
    public void markForRetry(String errorMessage, int retryDelaySeconds) {
        this.errorMessage = errorMessage;

        if (this.attempts >= this.maxAttempts) {
            // No más reintentos
            markAsFailed("Max attempts exceeded: " + errorMessage);
        } else {
            // Programar reintento
            this.status = ExtractionStatus.RETRYING;
            this.nextRetryAt = Instant.now().plusSeconds(retryDelaySeconds);
        }
    }

    /**
     * Verificar si puede ser procesada ahora
     */
    public boolean canBeProcessed() {
        if (status == ExtractionStatus.PENDING) {
            return true;
        }

        if (status == ExtractionStatus.RETRYING && nextRetryAt != null) {
            return Instant.now().isAfter(nextRetryAt);
        }

        return false;
    }

    /**
     * Verificar si está en estado terminal (no cambiará más)
     */
    public boolean isTerminal() {
        return status == ExtractionStatus.COMPLETED ||
               status == ExtractionStatus.FAILED ||
               status == ExtractionStatus.CANCELLED;
    }

    /**
     * Cancelar tarea
     */
    public void cancel() {
        this.status = ExtractionStatus.CANCELLED;
        this.completedAt = Instant.now();
    }
}
