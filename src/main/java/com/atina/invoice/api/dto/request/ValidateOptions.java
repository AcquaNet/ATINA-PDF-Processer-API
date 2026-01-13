package com.atina.invoice.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Validate configuration options")
public class ValidateOptions {

    @Schema(description = "Validate template schema", defaultValue = "false")
    private Boolean validateSchema = false;

    @Schema(description = "Pretty print JSON output", defaultValue = "true")
    private Boolean pretty = true;

    @Schema(description = "Return real Template JSON output", defaultValue = "true")
    private Boolean returnRealTemplate = true;

}