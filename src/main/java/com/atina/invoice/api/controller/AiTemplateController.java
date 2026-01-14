package com.atina.invoice.api.controller;

import com.atina.invoice.api.ai.service.TemplateGeneratorService;
import com.atina.invoice.api.dto.ai.TemplateGenerationRequest;
import com.atina.invoice.api.dto.ai.TemplateGenerationResponse;
import com.atina.invoice.api.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * AI Template Generator Controller
 * Generates extraction templates automatically using LLM
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Template Generator", description = "AI-powered template generation endpoints")
public class AiTemplateController {

    private final TemplateGeneratorService templateGeneratorService;

    /**
     * Generate template from PDF samples
     * 
     * POST /api/v1/ai/generate-template
     * 
     * Upload 1-2 PDF samples and provide description
     * AI analyzes PDFs and generates extraction template automatically
     */
    @PostMapping(value = "/generate-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Generate extraction template from PDF samples",
            description = "Upload 1-2 PDF examples. AI analyzes them and automatically generates an extraction template. " +
                         "Perfect for non-technical users who want to configure extraction without writing JSON/regex."
    )
    public ApiResponse<TemplateGenerationResponse> generateTemplate(
            @RequestPart("samples") MultipartFile[] pdfSamples,
            @RequestParam("description") String documentDescription,
            @RequestParam(value = "fields", required = false) String fieldHintsCommaSeparated,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "validate", required = false, defaultValue = "true") Boolean validate
    ) {
        log.info("AI template generation requested: {} samples, description: '{}'", 
                 pdfSamples.length, documentDescription);

        long start = System.currentTimeMillis();

        try {
            // Parse field hints
            List<String> fieldHints = null;
            if (fieldHintsCommaSeparated != null && !fieldHintsCommaSeparated.trim().isEmpty()) {
                fieldHints = Arrays.stream(fieldHintsCommaSeparated.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            // Build request
            TemplateGenerationRequest request = TemplateGenerationRequest.builder()
                    .pdfExamples(Arrays.asList(pdfSamples))
                    .documentDescription(documentDescription)
                    .fieldHints(fieldHints)
                    .documentType(documentType)
                    .validateTemplate(validate)
                    .build();

            // Generate template
            TemplateGenerationResponse response = templateGeneratorService.generateTemplate(request);

            long duration = System.currentTimeMillis() - start;

            log.info("Template generated: {} fields, confidence: {}, duration: {}ms",
                     response.getFieldsDetected() != null ? response.getFieldsDetected().size() : 0,
                     response.getConfidence(),
                     duration);

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Template generation failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Template generation failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
    }

}
