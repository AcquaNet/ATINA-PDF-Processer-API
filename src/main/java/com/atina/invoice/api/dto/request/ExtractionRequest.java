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
@Schema(description = "Invoice extraction request")
public class ExtractionRequest {

    @NotNull(message = "Docling JSON is required")
    @Schema(description = "Docling JSON document", required = true)
    private JsonNode docling;

    @NotNull(message = "Template JSON is required")
    @Schema(description = "Extraction template", required = true)
    private JsonNode template;

    @Valid
    @Schema(description = "Extraction options")
    private ExtractionOptions options;

    @Schema(description = "Custom correlation ID for tracing")
    private String correlationId;
}