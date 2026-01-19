package com.atina.invoice.api.model.enums;

/**
 * Tipo de protocolo de email
 */
public enum EmailType {
    /**
     * Internet Message Access Protocol
     * Protocolo más moderno, permite sincronización
     */
    IMAP,
    
    /**
     * Post Office Protocol version 3
     * Protocolo más antiguo, descarga y elimina del servidor
     */
    POP3
}
