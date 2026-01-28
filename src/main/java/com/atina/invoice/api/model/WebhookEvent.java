package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.WebhookEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Evento de webhook usando el patrón Transactional Outbox
 *
 * Este patrón garantiza que los webhooks nunca se pierdan:
 * 1. El evento se crea en la MISMA transacción que completa la extracción
 * 2. Si la transacción falla, ni la tarea ni el evento se guardan
 * 3. Si la transacción tiene éxito, ambos se guardan atómicamente
 * 4. Un worker separado (WebhookProcessor) envía los webhooks de forma asíncrona
 * 5. Los reintentos se manejan a nivel de base de datos, no en memoria
 *
 * Ventajas:
 * - Garantía de entrega (at-least-once delivery)
 * - Visibilidad completa de todos los webhooks
 * - Reintentos automáticos con exponential backoff
 * - Posibilidad de reintento manual
 * - Auditoría completa
 */
@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "idx_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_tenant", columnList = "tenant_id"),
        @Index(name = "idx_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID del tenant propietario
     */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * Tipo de evento
     * Valores: "extraction_email_completed", "extraction_task_completed"
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Tipo de entidad relacionada
     * Valores: "ProcessedEmail", "ExtractionTask"
     */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /**
     * ID de la entidad relacionada
     */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Payload completo del webhook en formato JSON
     * Contiene toda la información que se enviará al cliente
     */
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    /**
     * Estado actual del webhook
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private WebhookEventStatus status = WebhookEventStatus.PENDING;

    /**
     * Número de intentos de envío realizados
     */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /**
     * Número máximo de intentos permitidos
     */
    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    /**
     * Fecha/hora del próximo reintento
     * NULL = listo para enviar inmediatamente
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * Último mensaje de error
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Fecha/hora del último intento de envío
     */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    /**
     * Fecha/hora en que el webhook fue enviado exitosamente
     */
    @Column(name = "sent_at")
    private Instant sentAt;

    /**
     * Fecha de creación del evento
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

    // ========================================
    // State Transition Methods
    // ========================================

    /**
     * Marcar como "enviando"
     * Se llama justo antes de enviar el webhook HTTP
     */
    public void markAsSending() {
        this.status = WebhookEventStatus.SENDING;
        this.lastAttemptAt = Instant.now();
        this.attempts++;
    }

    /**
     * Marcar como "enviado exitosamente"
     * Estado final - webhook entregado con éxito
     */
    public void markAsSent() {
        this.status = WebhookEventStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Marcar como fallido
     * Si aún quedan reintentos: status = PENDING, calcula next_retry_at
     * Si se agotaron reintentos: status = FAILED (requiere intervención manual)
     *
     * @param error Mensaje de error
     */
    public void markAsFailed(String error) {
        this.lastError = error;

        if (this.attempts >= this.maxAttempts) {
            // Sin más reintentos - marcar como FAILED
            this.status = WebhookEventStatus.FAILED;
        } else {
            // Aún quedan reintentos - programar retry con exponential backoff
            this.status = WebhookEventStatus.PENDING;

            // Exponential backoff: 60s, 120s, 240s, 480s, 960s...
            int delaySeconds = 60 * (int) Math.pow(2, this.attempts - 1);
            this.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
        }
    }

    /**
     * Resetear para reintento manual
     * Usado cuando un administrador quiere reintentar un webhook FAILED
     */
    public void resetForRetry() {
        this.status = WebhookEventStatus.PENDING;
        this.attempts = 0;
        this.nextRetryAt = null;
        this.lastError = null;
    }
}
