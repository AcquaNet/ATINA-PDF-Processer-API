package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response details")
public class ErrorResponse {

    @Schema(description = "Error code", required = true, example = "VALIDATION_FAILED")
    private String code;

    @Schema(description = "Error message", required = true)
    private String message;

    @Schema(description = "Detailed error information")
    private List<String> details;

    @Schema(description = "Path that caused the error")
    private String path;
}