package com.atina.invoice.api.model.enums;

/**
 * Estados de una tarea de extracción de PDF
 */
public enum ExtractionStatus {
    /**
     * Tarea creada, esperando procesamiento
     */
    PENDING,

    /**
     * Tarea en proceso de extracción
     */
    PROCESSING,

    /**
     * Tarea falló pero se reintentará
     */
    RETRYING,

    /**
     * Extracción completada exitosamente
     */
    COMPLETED,

    /**
     * Extracción falló después de todos los reintentos
     */
    FAILED,

    /**
     * Tarea cancelada manualmente
     */
    CANCELLED
}
