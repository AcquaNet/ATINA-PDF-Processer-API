package com.atina.invoice.api.config;

import com.samskivert.mustache.Mustache;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para el sistema de notificaciones por email
 *
 * Lee las propiedades desde email.notifications.*
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "email.notifications")
public class EmailConfig {

    /**
     * Habilitar/deshabilitar envío de emails
     */
    private boolean enabled = true;

    /**
     * Email FROM (remitente)
     */
    private String from;

    /**
     * Nombre del remitente
     */
    private String fromName;

    /**
     * Email para reply-to
     */
    private String replyTo;

    /**
     * JMustache compiler bean para renderizar templates HTML
     *
     * Configuración:
     * - Delimiters: {{ }} (estándar Mustache)
     * - escapeHTML: false (porque controlamos los templates)
     * - defaultValue: "" (string vacío para variables faltantes)
     *
     * @return Compilador de JMustache configurado
     */
    @Bean
    public Mustache.Compiler mustacheCompiler() {
        return Mustache.compiler()
                .escapeHTML(false)        // Don't escape HTML (we control the templates)
                .defaultValue("");        // Empty string for missing variables
    }
}
