package com.atina.invoice.api.ai.service;

import com.atina.invoice.api.ai.prompt.TemplateGenerationPrompts;
import com.atina.invoice.api.ai.provider.LlmProvider;
import com.atina.invoice.api.dto.ai.TemplateGenerationRequest;
import com.atina.invoice.api.dto.ai.TemplateGenerationResponse;
import com.atina.invoice.api.dto.request.ValidateOptions;
import com.atina.invoice.api.service.DoclingService;
import com.atina.invoice.api.service.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Template Generator Service
 * Uses LLM to automatically generate extraction templates from PDF samples
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateGeneratorService {

    private final LlmProvider llmProvider;
    private final DoclingService doclingService;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;

    /**
     * Generate extraction template from PDF samples
     */
    public TemplateGenerationResponse generateTemplate(TemplateGenerationRequest request) {
        log.info("Starting template generation process");

        long startTime = System.currentTimeMillis();

        try {
            // 1. Validate request
            validateRequest(request);

            // 2. Convert PDFs to Docling JSON
            log.info("Converting {} PDF samples to Docling JSON",
                    getPdfCount(request));
            List<JsonNode> doclingJsonSamples = convertPdfsToDoclingJson(request);

            // 3. Build context for LLM
            Map<String, Object> context = buildContext(request, doclingJsonSamples);

            // 4. Select appropriate prompt
            String prompt = TemplateGenerationPrompts.getPromptForDocumentType(
                    request.getDocumentDescription()
            );

            log.info("Using LLM provider: {} ({})",
                    llmProvider.getProviderName(),
                    llmProvider.getModelName());

            // 5. Generate template using LLM
            String templateJson = llmProvider.generateTemplate(prompt, context);
            JsonNode template = objectMapper.readTree(templateJson);

            log.info("Template generated successfully");

            // 6. Validate generated template (if requested)
            TemplateGenerationResponse.ValidationResult validationResult = null;
            if (Boolean.TRUE.equals(request.getValidateTemplate())) {
                validationResult = validateGeneratedTemplate(template);
            }

            // 7. Calculate confidence score
            Double confidence = calculateConfidence(template, validationResult);

            // 8. Generate suggestions
            List<String> suggestions = generateSuggestions(template, doclingJsonSamples);

            // 9. Extract fields detected
            List<String> fieldsDetected = extractFieldNames(template);

            // 10. Build response
            long duration = System.currentTimeMillis() - startTime;

            return TemplateGenerationResponse.builder()
                    .template(template)
                    .templateJson(objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(template))
                    .confidence(confidence)
                    .suggestions(suggestions)
                    .validation(validationResult)
                    .llmProvider(llmProvider.getProviderName())
                    .modelUsed(llmProvider.getModelName())
                    .generatedAt(Instant.now())
                    .durationMs(duration)
                    .samplesAnalyzed(doclingJsonSamples.size())
                    .fieldsDetected(fieldsDetected)
                    .build();

        } catch (Exception e) {
            log.error("Template generation failed", e);
            throw new RuntimeException("Template generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate request
     */
    private void validateRequest(TemplateGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        int pdfCount = getPdfCount(request);
        if (pdfCount == 0) {
            throw new IllegalArgumentException("At least one PDF sample is required");
        }

        if (pdfCount > 5) {
            throw new IllegalArgumentException("Maximum 5 PDF samples allowed");
        }

        if (request.getDocumentDescription() == null ||
                request.getDocumentDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Document description is required");
        }
    }

    /**
     * Get PDF count from request
     */
    private int getPdfCount(TemplateGenerationRequest request) {
        int count = 0;
        if (request.getPdfExamplesBase64() != null) {
            count += request.getPdfExamplesBase64().size();
        }
        if (request.getPdfExamples() != null) {
            count += request.getPdfExamples().size();
        }
        return count;
    }

    /**
     * Convert PDFs to Docling JSON
     */
    private List<JsonNode> convertPdfsToDoclingJson(TemplateGenerationRequest request)
            throws Exception {
        List<JsonNode> doclingJsonSamples = new ArrayList<>();

        // From base64
        if (request.getPdfExamplesBase64() != null) {
            for (String base64 : request.getPdfExamplesBase64()) {
                // TODO: Convert base64 to MultipartFile and process
                log.warn("Base64 PDF processing not yet implemented");
            }
        }

        // From MultipartFiles
        if (request.getPdfExamples() != null) {
            for (MultipartFile pdf : request.getPdfExamples()) {
                log.debug("Converting PDF: {}", pdf.getOriginalFilename());
                JsonNode doclingJson = doclingService.convertPdf(pdf);

                // Extract json_content if wrapped
                JsonNode content = doclingJson.has("json_content") ?
                        doclingJson.get("json_content") : doclingJson;

                doclingJsonSamples.add(content);
            }
        }

        return doclingJsonSamples;
    }

    /**
     * Build context for LLM
     */
    private Map<String, Object> buildContext(TemplateGenerationRequest request,
                                             List<JsonNode> doclingJsonSamples) {
        Map<String, Object> context = new HashMap<>();

        context.put("documentDescription", request.getDocumentDescription());
        context.put("doclingJsonSamples", doclingJsonSamples);

        if (request.getFieldHints() != null && !request.getFieldHints().isEmpty()) {
            context.put("fieldHints", request.getFieldHints());
        }

        return context;
    }

    /**
     * Validate generated template
     */
    private TemplateGenerationResponse.ValidationResult validateGeneratedTemplate(
            JsonNode template) {
        try {
            // Create validation options
            ValidateOptions options = new ValidateOptions();
            options.setValidateSchema(true);
            options.setPretty(false);
            options.setReturnRealTemplate(false);

            // Call validation service
            JsonNode validationResult = validationService.validateTemplate(template, options);

            // Extract validation status
            boolean valid = validationResult.has("valid") &&
                    validationResult.get("valid").asBoolean();

            // Extract validation details
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            if (validationResult.has("validations")) {
                JsonNode validations = validationResult.get("validations");
                if (validations.isArray()) {
                    for (JsonNode validation : validations) {
                        String severity = validation.has("severity") ?
                                validation.get("severity").asText() : "error";
                        String message = validation.has("message") ?
                                validation.get("message").asText() : "Unknown error";

                        if ("error".equalsIgnoreCase(severity)) {
                            errors.add(message);
                        } else {
                            warnings.add(message);
                        }
                    }
                }
            }

            // Count blocks and rules
            int blocksCount = 0;
            int rulesCount = 0;

            if (validationResult.has("template")) {
                JsonNode templateInfo = validationResult.get("template");
                if (templateInfo.has("blocksCount")) {
                    blocksCount = templateInfo.get("blocksCount").asInt();
                }
                if (templateInfo.has("rulesCount")) {
                    rulesCount = templateInfo.get("rulesCount").asInt();
                }
            } else {
                // Fallback: count from template directly
                if (template.has("blocks")) {
                    blocksCount = template.get("blocks").size();
                    for (JsonNode block : template.get("blocks")) {
                        if (block.has("rules")) {
                            rulesCount += block.get("rules").size();
                        }
                    }
                }
            }

            return TemplateGenerationResponse.ValidationResult.builder()
                    .valid(valid)
                    .errors(errors)
                    .warnings(warnings)
                    .blocksCount(blocksCount)
                    .rulesCount(rulesCount)
                    .build();

        } catch (Exception e) {
            log.error("Template validation failed", e);
            return TemplateGenerationResponse.ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Validation failed: " + e.getMessage()))
                    .warnings(new ArrayList<>())
                    .build();
        }
    }

    /**
     * Calculate confidence score
     */
    private Double calculateConfidence(JsonNode template,
                                       TemplateGenerationResponse.ValidationResult validation) {
        double confidence = 1.0;

        // Reduce confidence if validation failed
        if (validation != null && !validation.getValid()) {
            confidence -= 0.3;
        }

        // Reduce confidence based on warnings
        if (validation != null && validation.getWarnings() != null) {
            confidence -= (validation.getWarnings().size() * 0.05);
        }

        // Increase confidence if has multiple rules
        if (validation != null && validation.getRulesCount() != null) {
            if (validation.getRulesCount() >= 5) {
                confidence += 0.1;
            }
        }

        // Clamp between 0 and 1
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Generate suggestions for improvement
     */
    private List<String> generateSuggestions(JsonNode template,
                                             List<JsonNode> doclingJsonSamples) {
        List<String> suggestions = new ArrayList<>();

        // Check if template has required fields
        boolean hasRequiredFields = false;
        if (template.has("blocks")) {
            for (JsonNode block : template.get("blocks")) {
                if (block.has("rules")) {
                    for (JsonNode rule : block.get("rules")) {
                        if (rule.has("required") && rule.get("required").asBoolean()) {
                            hasRequiredFields = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!hasRequiredFields) {
            suggestions.add("Consider marking critical fields as 'required: true'");
        }

        // Suggest testing
        suggestions.add("Test this template with additional documents to verify accuracy");

        // Suggest refinement if low rule count
        int rulesCount = 0;
        if (template.has("blocks")) {
            for (JsonNode block : template.get("blocks")) {
                if (block.has("rules")) {
                    rulesCount += block.get("rules").size();
                }
            }
        }

        if (rulesCount < 3) {
            suggestions.add("Template has few extraction rules. Consider adding more fields if needed");
        }

        return suggestions;
    }

    /**
     * Extract field names from template
     */
    private List<String> extractFieldNames(JsonNode template) {
        List<String> fields = new ArrayList<>();

        if (template.has("blocks")) {
            for (JsonNode block : template.get("blocks")) {
                if (block.has("rules")) {
                    for (JsonNode rule : block.get("rules")) {
                        if (rule.has("field")) {
                            fields.add(rule.get("field").asText());
                        }
                    }
                }
            }
        }

        return fields;
    }
}
