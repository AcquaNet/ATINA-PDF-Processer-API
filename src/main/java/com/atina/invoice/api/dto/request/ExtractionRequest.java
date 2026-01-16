package com.atina.invoice.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request unificado para extracción
 *
 * Soporta 3 formatos de input:
 * - JSON: docling y template como JsonNode (Content-Type: application/json)
 * - FILE: docling y template como MultipartFile (Content-Type: multipart/form-data)
 * - PATH: docling y template como paths en filesystem (Content-Type: application/json)
 *
 * El controller detecta automáticamente qué formato se está usando.
 */
@Data
public class ExtractionRequest {

    // ============================================================
    // Opción 1: JSON directo (application/json)
    // ============================================================

    /**
     * Docling JSON
     * Usado cuando Content-Type: application/json
     */
    private JsonNode docling;

    /**
     * Template JSON
     * Usado cuando Content-Type: application/json
     */
    private JsonNode template;

    // ============================================================
    // Opción 2: Files (multipart/form-data)
    // ============================================================

    /**
     * Archivo docling
     * Usado cuando Content-Type: multipart/form-data
     */
    private MultipartFile doclingFile;

    /**
     * Archivo template
     * Usado cuando Content-Type: multipart/form-data
     */
    private MultipartFile templateFile;

    // ============================================================
    // Opción 3: Paths en filesystem compartido (application/json)
    // ============================================================

    /**
     * Path al archivo docling en filesystem
     * Usado para entornos con filesystem compartido
     * Ejemplo: "/shared/invoices/invoice-001.json"
     */
    private String doclingPath;

    /**
     * Path al archivo template en filesystem
     * Usado para entornos con filesystem compartido
     * Ejemplo: "/shared/templates/standard-template.json"
     */
    private String templatePath;

    // ============================================================
    // Común para todas las opciones
    // ============================================================

    /**
     * Opciones de extracción
     * Opcional para todos los formatos
     */
    private ExtractionOptions options;

    /**
     * Valida que el request tiene al menos una modalidad válida
     *
     * @return true si es válido
     */
    public boolean isValid() {
        boolean hasJson = docling != null && template != null;
        boolean hasFiles = doclingFile != null && !doclingFile.isEmpty() &&
                templateFile != null && !templateFile.isEmpty();
        boolean hasPaths = doclingPath != null && !doclingPath.isBlank() &&
                templatePath != null && !templatePath.isBlank();

        return hasJson || hasFiles || hasPaths;
    }

    /**
     * Detecta el tipo de input que se está usando
     *
     * @return InputType
     * @throws IllegalArgumentException si no hay input válido
     */
    public InputType getInputType() {
        if (docling != null && template != null) {
            return InputType.JSON;
        }

        if (doclingFile != null && !doclingFile.isEmpty() &&
                templateFile != null && !templateFile.isEmpty()) {
            return InputType.FILE;
        }

        if (doclingPath != null && !doclingPath.isBlank() &&
                templatePath != null && !templatePath.isBlank()) {
            return InputType.PATH;
        }

        throw new IllegalArgumentException("No valid input provided. Must provide one of: JSON, Files, or Paths");
    }

    /**
     * Enum para tipos de input
     */
    public enum InputType {
        /**
         * JSON directo en body
         */
        JSON,

        /**
         * Archivos multipart
         */
        FILE,

        /**
         * Paths en filesystem
         */
        PATH
    }
}
