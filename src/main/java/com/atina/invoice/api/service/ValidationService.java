package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.dto.request.ValidateOptions;
import com.atina.pdfProcesser.PDFExtractionFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for template validation
 * Validates templates before extraction
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validate template structure and business rules
     */
    public JsonNode validateTemplate(JsonNode template, ValidateOptions options) {
        log.info("Starting extraction process");

        long startTime = System.currentTimeMillis();

        try {

            String templateJson = objectMapper.writeValueAsString(template);

            // Prepare options
            Map<String, Object> extractionOptions = buildOptions(options);

            // Call extraction facade
            String resultJson = PDFExtractionFacade.validateTemplate(templateJson, extractionOptions);

            // Parse result
            JsonNode result = objectMapper.readTree(resultJson);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Extraction completed successfully in {}ms", duration);

            return result;

        } catch (PDFExtractionFacade.ValidationFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            throw new com.atina.invoice.api.exception.ValidationException(
                    "Validation failed: " + e.getMessage(), e
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Extraction failed", e);
            throw new com.atina.invoice.api.exception.ExtractionException(
                    "Extraction failed: " + e.getMessage(), e
            );
        }
    }

    private Map<String, Object> buildOptions(ValidateOptions options) {
        Map<String, Object> map = new HashMap<>();

        if (options != null) {
            map.put("returnRealTemplate", options.getReturnRealTemplate() != null ? options.getReturnRealTemplate() : true);
            map.put("validateSchema", options.getValidateSchema() != null ? options.getValidateSchema() : true);
            map.put("pretty", options.getPretty() != null ? options.getPretty() : true);
        } else {
            // Defaults
            map.put("returnRealTemplate", true);
            map.put("validateSchema", true);
            map.put("pretty", true);
        }

        return map;
    }


}
