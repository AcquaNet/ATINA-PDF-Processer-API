package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.EmailProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Email procesado del sistema
 * Registra cada email que se procesa
 */
@Entity
@Table(name = "processed_emails", indexes = {
        @Index(name = "idx_email_uid", columnList = "email_account_id,email_uid", unique = true),
        @Index(name = "idx_processing_status", columnList = "processing_status"),
        @Index(name = "idx_from_address", columnList = "from_address"),
        @Index(name = "idx_processed_date", columnList = "processed_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant propietario
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Cuenta de email desde donde se procesó
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_account_id", nullable = false)
    private EmailAccount emailAccount;

    /**
     * Regla de sender aplicada (puede ser null si no matcheó ninguna)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_rule_id")
    private EmailSenderRule senderRule;

    // ========== Metadata del Email ==========

    /**
     * UID único del email en el servidor IMAP/POP3
     */
    @Column(name = "email_uid", nullable = false, length = 255)
    private String emailUid;

    /**
     * Subject del email
     */
    @Column(name = "subject", length = 1000)
    private String subject;

    /**
     * Email del remitente (solo el email)
     */
    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    /**
     * Remitente completo con nombre (ej: "John Doe <john@example.com>")
     */
    @Column(name = "from_addresses", columnDefinition = "TEXT")
    private String fromAddresses;

    /**
     * Lista de destinatarios (JSON array)
     */
    @Column(name = "to_addresses", columnDefinition = "TEXT")
    private String toAddresses;

    /**
     * Lista de CC (JSON array)
     */
    @Column(name = "cc_addresses", columnDefinition = "TEXT")
    private String ccAddresses;

    /**
     * Lista de BCC (JSON array)
     */
    @Column(name = "bcc_addresses", columnDefinition = "TEXT")
    private String bccAddresses;

    // ========== Fechas ==========

    /**
     * Fecha en que se envió el email (del header)
     */
    @Column(name = "sent_date")
    private Instant sentDate;

    /**
     * Fecha en que se recibió el email (del header)
     */
    @Column(name = "received_date")
    private Instant receivedDate;

    /**
     * Fecha en que se procesó el email
     */
    @Column(name = "processed_date", nullable = false)
    @Builder.Default
    private Instant processedDate = Instant.now();

    // ========== Estado de Procesamiento ==========

    /**
     * Estado del procesamiento
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    @Builder.Default
    private EmailProcessingStatus processingStatus = EmailProcessingStatus.PENDING;

    /**
     * Mensaje de error (si falló)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ========== Información de Attachments ==========

    /**
     * Total de attachments en el email
     */
    @Column(name = "total_attachments", nullable = false)
    @Builder.Default
    private Integer totalAttachments = 0;

    /**
     * Attachments que matchearon reglas y se procesaron
     */
    @Column(name = "processed_attachments", nullable = false)
    @Builder.Default
    private Integer processedAttachments = 0;

    // ========== Metadata File ==========

    /**
     * Path al archivo JSON con metadata completa del email
     */
    @Column(name = "metadata_file_path", length = 500)
    private String metadataFilePath;

    /**
     * Metadata JSON completo (como backup)
     */
    @Column(name = "raw_metadata", columnDefinition = "TEXT")
    private String rawMetadata;

    // ========== Notificaciones ==========

    /**
     * Se envió notificación de recepción
     */
    @Column(name = "received_notification_sent", nullable = false)
    @Builder.Default
    private Boolean receivedNotificationSent = false;

    /**
     * Fecha de envío de notificación de recepción
     */
    @Column(name = "received_notification_sent_at")
    private Instant receivedNotificationSentAt;

    /**
     * Se envió notificación de procesamiento completo
     */
    @Column(name = "processed_notification_sent", nullable = false)
    @Builder.Default
    private Boolean processedNotificationSent = false;

    /**
     * Fecha de envío de notificación de procesamiento
     */
    @Column(name = "processed_notification_sent_at")
    private Instant processedNotificationSentAt;

    // ========== Relaciones ==========

    /**
     * Attachments procesados
     */
    @OneToMany(mappedBy = "processedEmail", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProcessedAttachment> attachments = new ArrayList<>();

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
     * Agregar attachment procesado
     */
    public void addAttachment(ProcessedAttachment attachment) {
        attachments.add(attachment);
        attachment.setProcessedEmail(this);
    }

    /**
     * Marcar como completado
     */
    public void markAsCompleted() {
        this.processingStatus = EmailProcessingStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como fallido
     */
    public void markAsFailed(String errorMessage) {
        this.processingStatus = EmailProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    /**
     * Marcar como ignorado
     */
    public void markAsIgnored() {
        this.processingStatus = EmailProcessingStatus.IGNORED;
        this.updatedAt = Instant.now();
    }
}
