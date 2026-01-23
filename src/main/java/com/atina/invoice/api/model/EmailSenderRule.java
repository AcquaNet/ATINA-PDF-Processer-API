package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Regla de procesamiento por emisor de email
 * Define cómo procesar emails de un remitente específico
 */
@Entity
@Table(name = "email_sender_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sender_rule", 
                columnNames = {"tenant_id", "email_account_id", "sender_email"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSenderRule {

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
     * Cuenta de email asociada
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_account_id", nullable = false)
    private EmailAccount emailAccount;

    // ========== Identificación del Emisor ==========

    /**
     * Email del emisor (ej: javier.godino@atina-connection.com)
     */
    @Column(name = "sender_email", nullable = false, length = 255)
    private String senderEmail;

    /**
     * ID del sender (ej: "92455890")
     * Se usa para nombrar archivos y carpetas
     */
    @Column(name = "sender_id", nullable = false, length = 50)
    private String senderId;

    /**
     * Nombre del emisor (opcional)
     */
    @Column(name = "sender_name", length = 255)
    private String senderName;

    // ========== Templates de Notificación ==========

    /**
     * Nombre del template HTML para email recibido
     * (ej: "reply-mail-received.html")
     * Path completo: {tenant.template_base_path}/{template_email_received}
     */
    @Column(name = "template_email_received", length = 100)
    private String templateEmailReceived;

    /**
     * Nombre del template HTML para email procesado
     * (ej: "reply-mail-processed.html")
     */
    @Column(name = "template_email_processed", length = 100)
    private String templateEmailProcessed;

    // ========== Configuración de Procesamiento ==========

    /**
     * Enviar auto-reply cuando se recibe el email
     */
    @Column(name = "auto_reply_enabled", nullable = false)
    @Builder.Default
    private Boolean autoReplyEnabled = true;

    /**
     * Procesar attachments de este emisor
     */
    @Column(name = "process_enabled", nullable = false)
    @Builder.Default
    private Boolean processEnabled = true;

    // ========== Paths de Almacenamiento ==========

    /**
     * Path para archivos entrantes (inbound)
     * Si es null, usa el default del tenant
     */
    @Column(name = "inbound_path", length = 500)
    private String inboundPath;

    /**
     * Path para metadata de emails
     * Si es null, usa el default del tenant
     */
    @Column(name = "metadata_path", length = 500)
    private String metadataPath;

    // ========== Reglas de Procesamiento de Attachments ==========

    /**
     * Reglas de procesamiento de attachments
     */
    @OneToMany(mappedBy = "senderRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ruleOrder ASC")
    @Builder.Default
    private List<AttachmentProcessingRule> attachmentRules = new ArrayList<>();

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

    /**
     * Regla habilitada
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Descripción opcional
     */
    @Column(name = "description", length = 500)
    private String description;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ========== Helper Methods ==========

    /**
     * Agregar regla de attachment
     */
    public void addAttachmentRule(AttachmentProcessingRule rule) {
        attachmentRules.add(rule);
        rule.setSenderRule(this);
    }

    /**
     * Remover regla de attachment
     */
    public void removeAttachmentRule(AttachmentProcessingRule rule) {
        attachmentRules.remove(rule);
        rule.setSenderRule(null);
    }
}
