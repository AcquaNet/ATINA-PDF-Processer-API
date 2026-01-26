package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para actualizar un template de extracción
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExtractionTemplateRequest {

    @Size(max = 255, message = "Template name must not exceed 255 characters")
    private String templateName;

    private Boolean isActive;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
