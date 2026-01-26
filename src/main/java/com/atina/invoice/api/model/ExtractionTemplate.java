package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Template de extracción para PDFs
 *
 * Mapea (tenant, source) → template_path
 * Permite configurar qué template JSON usar para cada tipo de documento
 *
 * Ejemplo:
 * - Tenant: ACME Corp
 * - Source: "JDE" (de AttachmentProcessingRule.source)
 * - Template Path: /opt/invoice-app/templates/jde_invoice_template.json
 */
@Entity
@Table(name = "extraction_templates",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_tenant_source",
           columnNames = {"tenant_id", "source"}
       ),
       indexes = {
           @Index(name = "idx_tenant", columnList = "tenant_id"),
           @Index(name = "idx_source", columnList = "source")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant propietario del template
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * Source del documento (debe coincidir con AttachmentProcessingRule.source)
     * Ejemplos: "JDE", "SAP", "invoices", "purchase_orders"
     */
    @Column(name = "source", length = 50, nullable = false)
    private String source;

    /**
     * Path absoluto al archivo template JSON en el filesystem
     * Ejemplo: /opt/invoice-app/templates/jde_invoice_template.json
     */
    @Column(name = "template_path", length = 500, nullable = false)
    private String templatePath;

    /**
     * Si el template está activo
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Descripción del template
     */
    @Column(name = "description", length = 255)
    private String description;

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
}
