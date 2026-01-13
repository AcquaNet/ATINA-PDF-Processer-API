package com.atina.invoice.api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Template validation request")
public class ValidateTemplateRequest {

    @NotNull(message = "Template is required")
    @Schema(description = "Template to validate", required = true)
    private JsonNode template;

    @Valid
    @Schema(description = "Extraction options")
    private ValidateOptions options;

    @Schema(description = "Custom correlation ID for tracing")
    private String correlationId;

}