package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear un template de extracción
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExtractionTemplateRequest {

    @NotNull(message = "Tenant ID is required")
    private Long tenantId;

    @NotBlank(message = "Source is required")
    @Size(max = 50, message = "Source must not exceed 50 characters")
    private String source;

    @NotBlank(message = "Template name is required")
    @Size(max = 255, message = "Template name must not exceed 255 characters")
    private String templateName;

    @Builder.Default
    private Boolean isActive = true;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
}
