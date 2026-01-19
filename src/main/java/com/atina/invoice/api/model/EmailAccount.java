package com.atina.invoice.api.model;

import com.atina.invoice.api.model.enums.EmailType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cuenta de email configurada para polling
 * Representa una casilla de correo que se monitorea para procesar emails entrantes
 */
@Entity
@Table(name = "email_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_account", columnNames = {"tenant_id", "email_address"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant propietario de esta cuenta
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // ========== Configuración de Conexión ==========

    /**
     * Dirección de email (ej: soportecompany@gmail.com)
     */
    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    /**
     * Tipo de protocolo (IMAP o POP3)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 10)
    private EmailType emailType;

    /**
     * Host del servidor (ej: imap.gmail.com)
     */
    @Column(name = "host", nullable = false, length = 255)
    private String host;

    /**
     * Puerto del servidor (ej: 993 para IMAP SSL, 995 para POP3 SSL)
     */
    @Column(name = "port", nullable = false)
    private Integer port;

    /**
     * Usuario para autenticación (generalmente el email)
     */
    @Column(name = "username", nullable = false, length = 255)
    private String username;

    /**
     * Contraseña encriptada
     */
    @Column(name = "password", nullable = false, length = 500)
    private String password;

    /**
     * Usar SSL/TLS
     */
    @Column(name = "use_ssl", nullable = false)
    @Builder.Default
    private Boolean useSsl = true;

    // ========== Configuración de Polling ==========

    /**
     * Habilitar polling automático
     */
    @Column(name = "polling_enabled", nullable = false)
    @Builder.Default
    private Boolean pollingEnabled = true;

    /**
     * Intervalo de polling en minutos
     */
    @Column(name = "polling_interval_minutes", nullable = false)
    @Builder.Default
    private Integer pollingIntervalMinutes = 10;

    /**
     * Carpeta a monitorear (ej: INBOX)
     */
    @Column(name = "folder_name", nullable = false, length = 100)
    @Builder.Default
    private String folderName = "INBOX";

    // ========== Metadata de Polling ==========

    /**
     * Última vez que se intentó hacer polling (exitoso o no)
     */
    @Column(name = "last_poll_date")
    private Instant lastPollDate;

    /**
     * Última vez que se hizo polling exitoso
     */
    @Column(name = "last_successful_poll")
    private Instant lastSuccessfulPoll;

    /**
     * Último UID procesado (para IMAP)
     */
    @Column(name = "last_processed_uid", length = 255)
    private String lastProcessedUid;

    // ========== Metadata General ==========

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
     * Cuenta habilitada
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
}
