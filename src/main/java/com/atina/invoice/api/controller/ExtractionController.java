package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.*;
import com.atina.invoice.api.dto.response.*;
import com.atina.invoice.api.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Extraction", description = "Invoice extraction endpoints")
public class ExtractionController {
    
    private final ExtractionService extractionService;
    private final JobService jobService;

    public ExtractionController(ExtractionService extractionService, JobService jobService) {
        this.extractionService = extractionService;
        this.jobService = jobService;
    }

    @PostMapping("/extract")
    public ApiResponse<JsonNode> extract(@RequestBody ExtractionRequest request) {
        long start = System.currentTimeMillis();
        
        JsonNode result = extractionService.extract(
            request.getDocling(), 
            request.getTemplate(), 
            request.getOptions()
        );
        
        return ApiResponse.success(result, MDC.get("correlationId"), System.currentTimeMillis() - start);
    }

    @PostMapping("/extract/async")
    public ApiResponse<JobResponse> extractAsync(@RequestBody ExtractionRequest request) {
        String correlationId = MDC.get("correlationId");
        
        Job job = jobService.createJob(
            request.getDocling(),
            request.getTemplate(),
            request.getOptions(),
            correlationId
        );
        
        jobService.processJobAsync(job.getId());
        
        JobResponse response = JobResponse.builder()
            .jobId(job.getId())
            .status(job.getStatus())
            .statusUrl("/api/v1/extract/async/" + job.getId())
            .build();
        
        return ApiResponse.success(response);
    }

    @PostMapping("/validate-template")
    public ApiResponse<Map<String, Object>> validateTemplate(@RequestBody ValidateTemplateRequest request) {
        Map<String, Object> result = validationService.validateTemplate(
            request.getTemplate(),
            request.getStrictMode()
        );
        return ApiResponse.success(result);
    }
    
    // Implementar /extract/batch similar...
}