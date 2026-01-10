package com.atina.invoice.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Extraction configuration options")
public class ExtractionOptions {

    @Schema(description = "Include metadata in response", defaultValue = "true")
    private Boolean includeMeta = true;

    @Schema(description = "Include evidence (bounding boxes) in response", defaultValue = "false")
    private Boolean includeEvidence = false;

    @Schema(description = "Fail if validation errors found", defaultValue = "false")
    private Boolean failOnValidation = false;

    @Schema(description = "Validate template schema", defaultValue = "false")
    private Boolean validateSchema = false;

    @Schema(description = "Pretty print JSON output", defaultValue = "true")
    private Boolean pretty = true;
}