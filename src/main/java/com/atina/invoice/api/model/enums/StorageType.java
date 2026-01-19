package com.atina.invoice.api.model.enums;

/**
 * Tipo de almacenamiento para archivos procesados
 */
public enum StorageType {
    /**
     * Almacenamiento en file system local
     */
    LOCAL,

    /**
     * Almacenamiento en Amazon S3
     */
    S3,

    /**
     * Almacenamiento en ambos (local + S3)
     */
    BOTH
}
