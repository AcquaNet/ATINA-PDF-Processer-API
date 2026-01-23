package com.atina.invoice.api.model.enums;

/**
 * Estado de procesamiento de un attachment
 */
public enum AttachmentProcessingStatus {
    /**
     * Attachment pendiente de descargar
     */
    PENDING,
    
    /**
     * Attachment descargado exitosamente
     */
    DOWNLOADED,
    
    /**
     * En proceso de extracción de datos
     */
    EXTRACTING,
    
    /**
     * Procesamiento completado
     */
    COMPLETED,
    
    /**
     * Error durante el procesamiento
     */
    FAILED,
    
    /**
     * Attachment ignorado (no matchea reglas)
     */
    IGNORED
}
