package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateExtractionTemplateRequest;
import com.atina.invoice.api.dto.request.SaveTemplateContentRequest;
import com.atina.invoice.api.dto.request.UpdateExtractionTemplateRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.ExtractionTemplateResponse;
import com.atina.invoice.api.service.ExtractionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller para gestión de templates de extracción
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/extraction-templates")
@RequiredArgsConstructor
@Tag(name = "Extraction Templates", description = "Extraction template management for PDF processing")
@SecurityRequirement(name = "bearer-jwt")
public class ExtractionTemplateController {

    private final ExtractionTemplateService templateService;

    /**
     * Listar todos los templates
     */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] List all extraction templates",
            description = "Get all extraction templates from all tenants"
    )
    public ResponseEntity<ApiResponse<List<ExtractionTemplateResponse>>> getAllTemplates() {
        long start = System.currentTimeMillis();

        List<ExtractionTemplateResponse> templates = templateService.getAllTemplates();

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(templates, MDC.get("correlationId"), duration));
    }

    /**
     * Listar templates por tenant
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] List templates by tenant",
            description = "Get all extraction templates for a specific tenant"
    )
    public ResponseEntity<ApiResponse<List<ExtractionTemplateResponse>>> getTemplatesByTenant(
            @Parameter(description = "Tenant ID") @PathVariable Long tenantId) {

        long start = System.currentTimeMillis();

        List<ExtractionTemplateResponse> templates = templateService.getTemplatesByTenant(tenantId);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(templates, MDC.get("correlationId"), duration));
    }

    /**
     * Listar templates activos por tenant
     */
    @GetMapping("/tenant/{tenantId}/active")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] List active templates by tenant",
            description = "Get all active extraction templates for a specific tenant"
    )
    public ResponseEntity<ApiResponse<List<ExtractionTemplateResponse>>> getActiveTemplatesByTenant(
            @Parameter(description = "Tenant ID") @PathVariable Long tenantId) {

        long start = System.currentTimeMillis();

        List<ExtractionTemplateResponse> templates = templateService.getActiveTemplatesByTenant(tenantId);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(templates, MDC.get("correlationId"), duration));
    }

    /**
     * Obtener template por ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Get extraction template",
            description = "Get extraction template details by ID"
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> getTemplateById(
            @Parameter(description = "Template ID") @PathVariable Long id) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse template = templateService.getTemplateById(id);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(template, MDC.get("correlationId"), duration));
    }

    /**
     * Buscar template por tenant y source
     */
    @GetMapping("/tenant/{tenantId}/source/{source}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Get template by tenant and source",
            description = "Find extraction template for specific tenant and source (e.g., JDE, SAP)"
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> getTemplateByTenantAndSource(
            @Parameter(description = "Tenant ID") @PathVariable Long tenantId,
            @Parameter(description = "Source (e.g., JDE, SAP)") @PathVariable String source) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse template = templateService.getTemplateByTenantAndSource(tenantId, source);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(template, MDC.get("correlationId"), duration));
    }

    /**
     * Crear template
     */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Create extraction template",
            description = "Create a new extraction template for a tenant and source"
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> createTemplate(
            @Valid @RequestBody CreateExtractionTemplateRequest request) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse created = templateService.createTemplate(request);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, MDC.get("correlationId"), duration));
    }

    /**
     * Actualizar template
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Update extraction template",
            description = "Update an existing extraction template"
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> updateTemplate(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @Valid @RequestBody UpdateExtractionTemplateRequest request) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse updated = templateService.updateTemplate(id, request);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(updated, MDC.get("correlationId"), duration));
    }

    /**
     * Eliminar template
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Delete extraction template",
            description = "Delete an extraction template by ID"
    )
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @Parameter(description = "Template ID") @PathVariable Long id) {

        long start = System.currentTimeMillis();

        templateService.deleteTemplate(id);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(null, MDC.get("correlationId"), duration));
    }

    /**
     * Activar/desactivar template
     */
    @PostMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Toggle template status",
            description = "Activate or deactivate an extraction template"
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> toggleTemplateStatus(
            @Parameter(description = "Template ID") @PathVariable Long id) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse template = templateService.toggleTemplateStatus(id);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(template, MDC.get("correlationId"), duration));
    }

    /**
     * Guardar contenido JSON del template en el filesystem
     */
    @PostMapping("/{id}/save-content")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Save template content to filesystem",
            description = """
                    Save the JSON content of an extraction template to the filesystem.

                    The file will be saved to: {tenant.templateBasePath}/{template.templateName}

                    Example:
                    - Tenant templateBasePath: /config/templates
                    - Template name: jde_invoice_template.json
                    - Final path: /config/templates/jde_invoice_template.json

                    Set overwrite=true to replace existing file.
                    """
    )
    public ResponseEntity<ApiResponse<ExtractionTemplateResponse>> saveTemplateContent(
            @Parameter(description = "Template ID") @PathVariable Long id,
            @Valid @RequestBody SaveTemplateContentRequest request) {

        long start = System.currentTimeMillis();

        ExtractionTemplateResponse template = templateService.saveTemplateContent(id, request);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(template, MDC.get("correlationId"), duration));
    }
}
