package com.atina.invoice.api.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Regla de procesamiento de attachments
 * Define cómo procesar archivos adjuntos según su nombre (regex)
 */
@Entity
@Table(name = "attachment_processing_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rule_order", 
                columnNames = {"sender_rule_id", "rule_order"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentProcessingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Regla de sender asociada
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_rule_id", nullable = false)
    private EmailSenderRule senderRule;

    // ========== Configuración de la Regla ==========

    /**
     * Orden de ejecución (menor = mayor prioridad)
     * Importante: las reglas se evalúan en orden
     */
    @Column(name = "rule_order", nullable = false)
    private Integer ruleOrder;

    /**
     * Expresión regular para matchear el nombre del archivo
     * Ejemplos:
     * - ^Invoice+([0-9])+(.PDF|.pdf)$
     * - ^30716412527_.*\\.(?i:pdf)$
     * - ^Bank+([0-9])+(.CSV|.csv)$
     */
    @Column(name = "file_name_regex", nullable = false, length = 500)
    private String fileNameRegex;

    /**
     * Tipo de documento fuente
     * Valores: invoice, check, factura, remito, bank, atina, etc.
     */
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    /**
     * Sistema destino
     * Valores: jde, sap, etc.
     */
    @Column(name = "destination", nullable = false, length = 50)
    private String destination;

    /**
     * Método de procesamiento específico (opcional)
     * Ejemplo: "NorthBankCheck"
     */
    @Column(name = "processing_method", length = 100)
    private String processingMethod;

    /**
     * Regla habilitada
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Descripción de la regla
     */
    @Column(name = "description", length = 500)
    private String description;

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
     * Verificar si un nombre de archivo matchea esta regla
     */
    public boolean matches(String filename) {
        if (filename == null || !enabled) {
            return false;
        }
        return filename.matches(fileNameRegex);
    }
}
