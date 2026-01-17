package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.ValidateOptions;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.ValidationResult;
import com.atina.invoice.api.service.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * Controller para validación de templates
 *
 * Permite validar templates sin ejecutar extracción.
 * Soporta 3 formatos de input:
 * - JSON: Template como texto JSON
 * - File: Template como archivo
 * - Path: Template como path en filesystem
 *
 * @author Atina Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/validate")
@RequiredArgsConstructor
@Tag(name = "Validation", description = "Template validation endpoints")
public class ValidationController {

    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    /**
     * Validar template (UNIFICADO - FLEXIBLE)
     *
     * POST /api/v1/validate
     *
     * Template Input (elegir uno):
     * - template: JSON directo (texto)
     * - templateFile: Archivo JSON
     * - templatePath: Path al archivo
     *
     * Valida estructura y reglas de negocio sin ejecutar extracción.
     *
     * IMPORTANTE - Códigos HTTP:
     * - 200 OK: Validación ejecutada correctamente (template válido o inválido)
     * - 400 Bad Request: Request mal formado (falta template, JSON inválido)
     * - 500 Internal Server Error: Error del servidor
     */
    @PostMapping(consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE
    })
    @Operation(
            summary = "Validate extraction template",
            description = """
                    Validate template structure and business rules without executing extraction.
                    
                    Template Input (choose one):
                    - template: JSON object as string
                    - templateFile: Upload template file
                    - templatePath: Path to template in filesystem
                    
                    Returns HTTP 200 with validation result:
                    - success: true (validation executed)
                    - data.valid: true/false (template valid or not)
                    - data.errors: array of validation errors (if any)
                    - data.warnings: array of warnings (if any)
                    
                    Returns HTTP 400 if request is malformed.
                    """
    )
    public ApiResponse<ValidationResult> validateTemplate(
            // Template inputs
            @RequestPart(value = "template", required = false) String templateJson,
            @RequestPart(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestPart(value = "templatePath", required = false) String templatePath,

            // Options
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("Template validation requested");

        long start = System.currentTimeMillis();

        try {
            // 1. Validar input
            validateInput(templateJson, templateFile, templatePath);

            // 2. Parsear options
            ValidateOptions options = parseOptions(optionsJson);

            // 3. Procesar template según tipo de input
            JsonNode template = processTemplate(templateJson, templateFile, templatePath);

            // 4. Validar template
            JsonNode rawResult = validationService.validateTemplate(template, options);

            // 5. Convertir a ValidationResult estructurado (incluir template original)
            ValidationResult result = convertToValidationResult(rawResult, template);

            long duration = System.currentTimeMillis() - start;

            // Log resultado
            if (result.isValid()) {
                log.info("Template validation completed successfully in {}ms - Template is VALID", duration);
            } else {
                log.info("Template validation completed in {}ms - Template is INVALID ({} errors, {} warnings)",
                        duration, result.getErrors().size(), result.getWarnings().size());
            }

            // CRÍTICO: Siempre retornar success=true con HTTP 200
            // El campo valid indica si el template es válido o no
            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (IllegalArgumentException e) {
            // Request mal formado (falta template, múltiples inputs, etc)
            log.error("Validation request malformed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );

        } catch (Exception e) {
            // Error del servidor
            log.error("Template validation failed due to server error", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    "Template validation failed: " + e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );
        }
    }

    // ============================================================
    // PROCESSING METHODS
    // ============================================================

    /**
     * Procesa Template según tipo de input
     *
     * @param templateJson Template como JSON texto (opcional)
     * @param templateFile Template como archivo (opcional)
     * @param templatePath Path al template (opcional)
     * @return Template JSON
     * @throws IOException si hay error de lectura
     */
    private JsonNode processTemplate(String templateJson, MultipartFile templateFile,
                                     String templatePath) throws IOException {

        if (templateJson != null && !templateJson.isBlank()) {
            // Opción 1: Template como JSON texto
            log.debug("Processing template from JSON text");
            return objectMapper.readTree(templateJson);

        } else if (templateFile != null && !templateFile.isEmpty()) {
            // Opción 2: Template como File
            log.debug("Processing template from file: {}", templateFile.getOriginalFilename());
            return objectMapper.readTree(templateFile.getInputStream());

        } else if (templatePath != null && !templatePath.isBlank()) {
            // Opción 3: Template como Path
            log.debug("Processing template from path: {}", templatePath);
            return objectMapper.readTree(new File(templatePath));

        } else {
            // Este caso no debería ocurrir por validateInput()
            throw new IllegalArgumentException(
                    "No template input provided. Must provide one of: template, templateFile, or templatePath"
            );
        }
    }

    /**
     * Convierte resultado crudo del ValidationService a ValidationResult estructurado
     */
    private ValidationResult convertToValidationResult(JsonNode rawResult, JsonNode originalTemplate) {
        ValidationResult result = new ValidationResult();

        // Siempre incluir el template procesado si está disponible
        if (rawResult.has("processedTemplate")) {
            result.setProcessedTemplate(rawResult.get("processedTemplate"));
        } else if (rawResult.has("template")) {
            result.setProcessedTemplate(rawResult.get("template"));
        } else {
            // Si el servicio no retorna template procesado, usar el original
            result.setProcessedTemplate(originalTemplate);
        }

        // Si el servicio retorna estructura con "validations"
        if (rawResult.has("validations")) {
            JsonNode validations = rawResult.get("validations");

            for (JsonNode validation : validations) {
                String path = validation.has("path") ? validation.get("path").asText() : "unknown";
                String type = validation.has("type") ? validation.get("type").asText() : "error";
                String message = validation.has("message") ? validation.get("message").asText() : "Validation error";

                // Crear error estructurado
                ValidationResult.ValidationError error = new ValidationResult.ValidationError();
                error.setPath(path);
                error.setType(type);
                error.setMessage(cleanErrorMessage(message));

                result.getErrors().add(error);
            }

            // Si hay errores, template es inválido
            result.setValid(result.getErrors().isEmpty());
        }
        // Si el servicio retorna estructura estándar con valid/errors/warnings
        else if (rawResult.has("valid")) {
            result.setValid(rawResult.get("valid").asBoolean());

            if (rawResult.has("errors")) {
                for (JsonNode error : rawResult.get("errors")) {
                    ValidationResult.ValidationError err = objectMapper.convertValue(
                            error, ValidationResult.ValidationError.class);
                    result.getErrors().add(err);
                }
            }

            if (rawResult.has("warnings")) {
                for (JsonNode warning : rawResult.get("warnings")) {
                    ValidationResult.ValidationWarning warn = objectMapper.convertValue(
                            warning, ValidationResult.ValidationWarning.class);
                    result.getWarnings().add(warn);
                }
            }
        }
        // Fallback: asumir válido si no hay información
        else {
            result.setValid(true);
        }

        return result;
    }

    /**
     * Limpia mensaje de error para hacerlo más legible
     */
    private String cleanErrorMessage(String message) {
        if (message == null) {
            return "Validation error";
        }

        // Remover "Template validation failed:\n  - " del principio
        message = message.replace("Template validation failed:\n  - ", "");
        message = message.replace("Template validation failed:", "").trim();

        return message;
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Valida que se haya proporcionado el template
     *
     * @throws IllegalArgumentException si falta el template
     */
    private void validateInput(String templateJson, MultipartFile templateFile, String templatePath) {
        boolean hasTemplateJson = templateJson != null && !templateJson.isBlank();
        boolean hasTemplateFile = templateFile != null && !templateFile.isEmpty();
        boolean hasTemplatePath = templatePath != null && !templatePath.isBlank();

        // Validar que al menos uno esté presente
        if (!hasTemplateJson && !hasTemplateFile && !hasTemplatePath) {
            throw new IllegalArgumentException(
                    "Template input is required. Must provide one of: 'template', 'templateFile', or 'templatePath'"
            );
        }

        // Validar que no se proporcionen múltiples
        int inputCount = (hasTemplateJson ? 1 : 0) +
                (hasTemplateFile ? 1 : 0) +
                (hasTemplatePath ? 1 : 0);

        if (inputCount > 1) {
            throw new IllegalArgumentException(
                    "Cannot provide multiple template inputs. Choose one of: 'template', 'templateFile', or 'templatePath'"
            );
        }
    }

    /**
     * Parsea options desde JSON string
     */
    private ValidateOptions parseOptions(String optionsJson) throws IOException {
        if (optionsJson == null || optionsJson.isEmpty() || optionsJson.equals("{}")) {
            return new ValidateOptions();
        }

        return objectMapper.readValue(optionsJson, ValidateOptions.class);
    }
}

