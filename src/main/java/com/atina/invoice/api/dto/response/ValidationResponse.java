package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Template validation response")
public class ValidationResponse {

    @Schema(description = "Whether template is valid", required = true)
    private boolean valid;

    @Schema(description = "Template summary")
    private TemplateSummary template;

    @Schema(description = "Validation issues found")
    private List<ValidationIssue> validations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateSummary {
        private String templateId;
        private int blocksCount;
        private int rulesCount;
        private Map<String, Integer> ruleTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String severity;  // ERROR, WARNING, INFO
        private String path;
        private String message;
    }
}