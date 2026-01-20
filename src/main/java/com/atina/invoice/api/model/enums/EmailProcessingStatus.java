package com.atina.invoice.api.model.enums;

/**
 * Estados posibles de procesamiento de un email
 */
public enum EmailProcessingStatus {

    /**
     * Email pendiente de procesar
     */
    PENDING,

    /**
     * Email en proceso de descarga/procesamiento
     */
    PROCESSING,

    /**
     * Email procesado completamente (todos los attachments procesados)
     */
    COMPLETED,

    /**
     * Email procesado pero con errores
     */
    FAILED,

    /**
     * Email ignorado (no matcheó ninguna regla de sender o procesamiento deshabilitado)
     */
    IGNORED
}
