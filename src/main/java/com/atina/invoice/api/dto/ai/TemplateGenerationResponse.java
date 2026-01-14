package com.atina.invoice.api.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Template Generation Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateGenerationResponse {

    /**
     * Generated template JSON
     */
    private JsonNode template;

    /**
     * Template as JSON string
     */
    private String templateJson;

    /**
     * Confidence score (0.0 - 1.0)
     * Based on pattern quality and coverage
     */
    private Double confidence;

    /**
     * Suggestions for improvement
     */
    private List<String> suggestions;

    /**
     * Validation results (if validated)
     */
    private ValidationResult validation;

    /**
     * LLM provider used
     */
    private String llmProvider;

    /**
     * Model used
     */
    private String modelUsed;

    /**
     * Generation timestamp
     */
    private Instant generatedAt;

    /**
     * Duration in milliseconds
     */
    private Long durationMs;

    /**
     * Number of samples analyzed
     */
    private Integer samplesAnalyzed;

    /**
     * Fields detected in template
     */
    private List<String> fieldsDetected;

    /**
     * Validation result details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private Boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private Integer blocksCount;
        private Integer rulesCount;
    }
}
