package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.ValidateOptions;
import com.atina.invoice.api.dto.response.ApiResponse;
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
                    
                    Returns validation result with any errors or warnings found.
                    """
    )
    public ApiResponse<JsonNode> validateTemplate(
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
            JsonNode result = validationService.validateTemplate(template, options);

            long duration = System.currentTimeMillis() - start;

            log.info("Template validation completed in {}ms", duration);

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Template validation failed", e);
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
