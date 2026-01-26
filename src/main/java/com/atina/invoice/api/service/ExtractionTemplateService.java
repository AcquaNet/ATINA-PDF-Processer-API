package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateExtractionTemplateRequest;
import com.atina.invoice.api.dto.request.UpdateExtractionTemplateRequest;
import com.atina.invoice.api.dto.response.ExtractionTemplateResponse;
import com.atina.invoice.api.model.ExtractionTemplate;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.repository.ExtractionTemplateRepository;
import com.atina.invoice.api.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar templates de extracción
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionTemplateService {

    private final ExtractionTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;

    /**
     * Listar todos los templates
     */
    public List<ExtractionTemplateResponse> getAllTemplates() {
        log.debug("Fetching all extraction templates");

        List<ExtractionTemplate> templates = templateRepository.findAll();

        return templates.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Listar templates por tenant
     */
    public List<ExtractionTemplateResponse> getTemplatesByTenant(Long tenantId) {
        log.debug("Fetching extraction templates for tenant: {}", tenantId);

        List<ExtractionTemplate> templates = templateRepository.findByTenantId(tenantId);

        return templates.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Listar templates activos por tenant
     */
    public List<ExtractionTemplateResponse> getActiveTemplatesByTenant(Long tenantId) {
        log.debug("Fetching active extraction templates for tenant: {}", tenantId);

        List<ExtractionTemplate> templates = templateRepository
                .findByTenantIdAndIsActive(tenantId, true);

        return templates.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener template por ID
     */
    public ExtractionTemplateResponse getTemplateById(Long id) {
        log.debug("Fetching extraction template by ID: {}", id);

        ExtractionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        return toResponse(template);
    }

    /**
     * Buscar template por tenant y source
     */
    public ExtractionTemplateResponse getTemplateByTenantAndSource(Long tenantId, String source) {
        log.debug("Fetching extraction template for tenant {} and source {}", tenantId, source);

        ExtractionTemplate template = templateRepository
                .findByTenantIdAndSource(tenantId, source)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Template not found for tenant %d and source %s", tenantId, source)
                ));

        return toResponse(template);
    }

    /**
     * Crear template
     */
    @Transactional
    public ExtractionTemplateResponse createTemplate(CreateExtractionTemplateRequest request) {
        log.info("Creating extraction template for tenant {} and source {}",
                request.getTenantId(), request.getSource());

        // Validar que el tenant existe
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + request.getTenantId()));

        // Validar que no existe ya un template para este tenant y source
        if (templateRepository.existsByTenantIdAndSource(request.getTenantId(), request.getSource())) {
            throw new IllegalArgumentException(
                    String.format("Template already exists for tenant %d and source %s",
                            request.getTenantId(), request.getSource())
            );
        }

        // Validar que el archivo existe (opcional)
        validateTemplateFile(request.getTemplatePath());

        // Crear template
        ExtractionTemplate template = ExtractionTemplate.builder()
                .tenant(tenant)
                .source(request.getSource())
                .templatePath(request.getTemplatePath())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .description(request.getDescription())
                .build();

        template = templateRepository.save(template);

        log.info("Extraction template created successfully: {}", template.getId());

        return toResponse(template);
    }

    /**
     * Actualizar template
     */
    @Transactional
    public ExtractionTemplateResponse updateTemplate(Long id, UpdateExtractionTemplateRequest request) {
        log.info("Updating extraction template: {}", id);

        ExtractionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        // Actualizar campos si se proporcionan
        if (request.getTemplatePath() != null) {
            validateTemplateFile(request.getTemplatePath());
            template.setTemplatePath(request.getTemplatePath());
        }

        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }

        template = templateRepository.save(template);

        log.info("Extraction template updated successfully: {}", id);

        return toResponse(template);
    }

    /**
     * Eliminar template
     */
    @Transactional
    public void deleteTemplate(Long id) {
        log.info("Deleting extraction template: {}", id);

        if (!templateRepository.existsById(id)) {
            throw new IllegalArgumentException("Template not found: " + id);
        }

        templateRepository.deleteById(id);

        log.info("Extraction template deleted successfully: {}", id);
    }

    /**
     * Activar/desactivar template
     */
    @Transactional
    public ExtractionTemplateResponse toggleTemplateStatus(Long id) {
        log.info("Toggling extraction template status: {}", id);

        ExtractionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

        template.setIsActive(!template.getIsActive());
        template = templateRepository.save(template);

        log.info("Extraction template status toggled to {}: {}", template.getIsActive(), id);

        return toResponse(template);
    }

    /**
     * Validar que el archivo template existe
     * (Opcional - puede omitirse si los templates se crean después)
     */
    private void validateTemplateFile(String templatePath) {
        File file = new File(templatePath);

        if (!file.exists()) {
            log.warn("Template file does not exist: {} (will be created later)", templatePath);
            // No lanzar excepción, solo advertir
            // throw new IllegalArgumentException("Template file does not exist: " + templatePath);
        }

        if (file.exists() && !file.canRead()) {
            throw new IllegalArgumentException("Template file is not readable: " + templatePath);
        }
    }

    /**
     * Convertir entidad a DTO de respuesta
     */
    private ExtractionTemplateResponse toResponse(ExtractionTemplate template) {
        return ExtractionTemplateResponse.builder()
                .id(template.getId())
                .tenantId(template.getTenant().getId())
                .tenantCode(template.getTenant().getTenantCode())
                .tenantName(template.getTenant().getTenantName())
                .source(template.getSource())
                .templatePath(template.getTemplatePath())
                .isActive(template.getIsActive())
                .description(template.getDescription())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
