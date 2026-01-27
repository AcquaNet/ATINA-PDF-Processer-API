package com.atina.invoice.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para guardar el contenido JSON de un template en el filesystem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveTemplateContentRequest {

    /**
     * Contenido JSON del template
     * Puede ser un objeto JSON completo con los campos del template
     */
    @NotNull(message = "Template content is required")
    private JsonNode templateContent;

    /**
     * Si debe sobrescribir el archivo si ya existe (default: false)
     */
    @Builder.Default
    private Boolean overwrite = false;
}
