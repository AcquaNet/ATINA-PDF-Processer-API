package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de validación de template
 *
 * Estructura clara para respuestas de validación:
 * - valid: true/false (indica si el template es válido)
 * - processedTemplate: template procesado/normalizado (si aplica)
 * - errors: lista de errores encontrados
 * - warnings: lista de advertencias encontradas
 *
 * @author Atina Team
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResult {

    /**
     * Indica si el template es válido
     * - true: Template válido, sin errores
     * - false: Template inválido, con errores
     */
    private boolean valid;

    /**
     * Template procesado/normalizado después de la validación
     * Incluye valores por defecto aplicados, normalizaciones, etc.
     * Se retorna siempre que la validación se complete
     */
    private JsonNode processedTemplate;

    /**
     * Lista de errores de validación
     * Vacía si valid=true
     */
    private List<ValidationError> errors = new ArrayList<>();

    /**
     * Lista de advertencias
     * Pueden existir incluso si valid=true
     */
    private List<ValidationWarning> warnings = new ArrayList<>();

    /**
     * Error de validación
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        /**
         * Path del campo con error
         * Ejemplo: "$.version", "$.fields.total"
         */
        private String path;

        /**
         * Tipo de error
         * Ejemplo: "required", "format", "type"
         */
        private String type;

        /**
         * Mensaje descriptivo del error
         */
        private String message;

        /**
         * Código de error (opcional)
         * Ejemplo: "ERR_INVALID_FORMAT"
         */
        private String code;

        public ValidationError(String path, String message) {
            this.path = path;
            this.message = message;
        }

        public ValidationError(String path, String type, String message) {
            this.path = path;
            this.type = type;
            this.message = message;
        }
    }

    /**
     * Advertencia de validación
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationWarning {
        /**
         * Path del campo con advertencia
         * Ejemplo: "$.fields.notes"
         */
        private String path;

        /**
         * Tipo de advertencia
         * Ejemplo: "missing_validation", "deprecated"
         */
        private String type;

        /**
         * Mensaje descriptivo de la advertencia
         */
        private String message;

        /**
         * Código de advertencia (opcional)
         * Ejemplo: "WARN_NO_VALIDATION"
         */
        private String code;

        public ValidationWarning(String path, String message) {
            this.path = path;
            this.message = message;
        }

        public ValidationWarning(String path, String type, String message) {
            this.path = path;
            this.type = type;
            this.message = message;
        }
    }

    // ============================================================
    // CONVENIENCE METHODS
    // ============================================================

    /**
     * Agrega un error de validación
     */
    public void addError(String path, String message) {
        this.errors.add(new ValidationError(path, message));
        this.valid = false;  // Si hay errores, es inválido
    }

    /**
     * Agrega un error de validación con tipo
     */
    public void addError(String path, String type, String message) {
        this.errors.add(new ValidationError(path, type, message));
        this.valid = false;  // Si hay errores, es inválido
    }

    /**
     * Agrega una advertencia
     */
    public void addWarning(String path, String message) {
        this.warnings.add(new ValidationWarning(path, message));
    }

    /**
     * Agrega una advertencia con tipo
     */
    public void addWarning(String path, String type, String message) {
        this.warnings.add(new ValidationWarning(path, type, message));
    }

    /**
     * Verifica si hay errores
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Verifica si hay advertencias
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Cuenta total de problemas (errores + advertencias)
     */
    public int getTotalIssues() {
        return errors.size() + warnings.size();
    }
}
