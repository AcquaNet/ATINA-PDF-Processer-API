package com.atina.invoice.api.service;

import com.atina.pdfProcesser.PDFExtractionFacade;
import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    public JsonNode extract(JsonNode docling, JsonNode template, ExtractionOptions options) {
        log.info("Starting extraction process");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Convert JsonNode to String
            JsonNode doclingContent = docling.has("json_content") ? docling.get("json_content") : docling;
            String doclingJson = objectMapper.writeValueAsString(doclingContent);
            String templateJson = objectMapper.writeValueAsString(template);
            
            // Prepare options
            Map<String, Object> extractionOptions = buildOptions(options);
            
            // Call extraction facade
            String resultJson = PDFExtractionFacade.processPDF(doclingJson, templateJson, extractionOptions);
            
            // Parse result
            JsonNode result = objectMapper.readTree(resultJson);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Extraction completed successfully in {}ms", duration);
            
            // Track metrics
            metricsService.recordExtractionSuccess();

            
            return result;
            
        } catch (PDFExtractionFacade.ValidationFailedException e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordExtractionFailure();
            throw new com.atina.invoice.api.exception.ValidationException(
                "Validation failed: " + e.getMessage(), e
            );
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordExtractionFailure();
            log.error("Extraction failed", e);
            throw new com.atina.invoice.api.exception.ExtractionException(
                "Extraction failed: " + e.getMessage(), e
            );
        }
    }

    private Map<String, Object> buildOptions(ExtractionOptions options) {
        Map<String, Object> map = new HashMap<>();
        
        if (options != null) {
            map.put("includeMeta", options.getIncludeMeta() != null ? options.getIncludeMeta() : true);
            map.put("includeEvidence", options.getIncludeEvidence() != null ? options.getIncludeEvidence() : false);
            map.put("failOnValidation", options.getFailOnValidation() != null ? options.getFailOnValidation() : false);
            map.put("validateSchema", options.getValidateSchema() != null ? options.getValidateSchema() : false);
            map.put("pretty", options.getPretty() != null ? options.getPretty() : true);
        } else {
            // Defaults
            map.put("includeMeta", true);
            map.put("includeEvidence", false);
            map.put("failOnValidation", false);
            map.put("validateSchema", false);
            map.put("pretty", true);
        }
        
        return map;
    }
}