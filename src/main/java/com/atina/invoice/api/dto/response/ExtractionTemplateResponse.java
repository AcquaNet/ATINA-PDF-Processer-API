package com.atina.invoice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response para template de extracción
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionTemplateResponse {

    private Long id;
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private String tenantTemplateBasePath;
    private String source;
    private String templateName;
    private String fullTemplatePath; // Calculado: tenantTemplateBasePath + "/" + templateName
    private Boolean isActive;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
