// ============================================
// FILE: controller/ExtractionController.java
// ============================================

package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.BatchExtractionRequest;
import com.atina.invoice.api.dto.request.ExtractionRequest;
import com.atina.invoice.api.dto.request.ValidateTemplateRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.JobResponse;
import com.atina.invoice.api.dto.response.ValidationResponse;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.service.ExtractionService;
import com.atina.invoice.api.service.JobService;
import com.atina.invoice.api.service.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Extraction controller
 * Handles invoice data extraction operations (sync, async, batch, validation)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Extraction", description = "Invoice data extraction endpoints")
public class ExtractionController {

    private final ExtractionService extractionService;
    private final JobService jobService;
    private final ValidationService validationService;

    /**
     * Synchronous extraction
     * 
     * POST /api/v1/extract
     * 
     * Extracts invoice data immediately and returns result
     * Use for small/fast extractions
     */
    @PostMapping("/extract")
    @Operation(
        summary = "Extract invoice data (synchronous)",
        description = "Extract invoice data from Docling JSON using template. Returns result immediately."
    )
    public ApiResponse<JsonNode> extract(@Valid @RequestBody ExtractionRequest request) {
        log.info("Synchronous extraction requested");
        
        long start = System.currentTimeMillis();
        
        // Extract data
        JsonNode result = extractionService.extract(
            request.getDocling(),
            request.getTemplate(),
            request.getOptions()
        );
        
        long duration = System.currentTimeMillis() - start;
        
        log.info("Extraction completed in {}ms", duration);
        
        return ApiResponse.success(result, MDC.get("correlationId"), duration);
    }

    /**
     * Asynchronous extraction
     * 
     * POST /api/v1/extract/async
     * 
     * Creates a job and processes extraction in background
     * Use for large/slow extractions
     */
    @PostMapping("/extract/async")
    @Operation(
        summary = "Extract invoice data (asynchronous)",
        description = "Create extraction job. Returns job ID immediately, processing happens in background."
    )
    public ApiResponse<JobResponse> extractAsync(@Valid @RequestBody ExtractionRequest request) {
        log.info("Async extraction requested");
        
        long start = System.currentTimeMillis();
        
        // Create job
        Job job = jobService.createJob(request);
        
        // Process async
        jobService.processJobAsync(job.getId());
        
        // Build response
        JobResponse response = buildJobResponse(job);
        
        long duration = System.currentTimeMillis() - start;
        
        log.info("Async job created: {} ({}ms)", job.getId(), duration);
        
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }

    /**
     * Get job status
     * 
     * GET /api/v1/extract/async/{jobId}
     * 
     * Returns current status of an async extraction job
     */
    @GetMapping("/extract/async/{jobId}")
    @Operation(
        summary = "Get extraction job status",
        description = "Check status and progress of an asynchronous extraction job"
    )
    public ApiResponse<JobResponse> getJobStatus(@PathVariable String jobId) {
        log.debug("Job status requested: {}", jobId);
        
        long start = System.currentTimeMillis();
        
        // Get job
        Job job = jobService.getJob(jobId);
        
        // Build response
        JobResponse response = buildJobResponse(job);
        
        long duration = System.currentTimeMillis() - start;
        
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }

    /**
     * Get job result
     * 
     * GET /api/v1/extract/async/{jobId}/result
     * 
     * Returns the extraction result if job is completed
     */
    @GetMapping("/extract/async/{jobId}/result")
    @Operation(
        summary = "Get extraction job result",
        description = "Get the extraction result for a completed job"
    )
    public ApiResponse<JsonNode> getJobResult(@PathVariable String jobId) {
        log.debug("Job result requested: {}", jobId);
        
        long start = System.currentTimeMillis();
        
        // Get result
        JsonNode result = jobService.getJobResult(jobId);
        
        long duration = System.currentTimeMillis() - start;
        
        return ApiResponse.success(result, MDC.get("correlationId"), duration);
    }

    /**
     * Batch extraction
     * 
     * POST /api/v1/extract/batch
     * 
     * Process multiple documents with the same template
     */
    @PostMapping("/extract/batch")
    @Operation(
        summary = "Batch extraction",
        description = "Extract data from multiple documents using the same template"
    )
    public ApiResponse<Map<String, Object>> extractBatch(@Valid @RequestBody BatchExtractionRequest request) {
        log.info("Batch extraction requested for {} documents", request.getDocuments().size());
        
        long start = System.currentTimeMillis();
        
        Map<String, Object> results = new HashMap<>();
        results.put("totalDocuments", request.getDocuments().size());
        results.put("results", new java.util.ArrayList<>());
        
        int successCount = 0;
        int failureCount = 0;
        
        // Process each document
        for (var doc : request.getDocuments()) {
            try {
                JsonNode result = extractionService.extract(
                    doc.getDocling(),
                    request.getTemplate(),
                    request.getOptions()
                );
                
                Map<String, Object> docResult = new HashMap<>();
                docResult.put("id", doc.getId());
                docResult.put("success", true);
                docResult.put("data", result);
                
                ((java.util.List) results.get("results")).add(docResult);
                successCount++;
                
            } catch (Exception e) {
                log.error("Batch extraction failed for document: {}", doc.getId(), e);
                
                Map<String, Object> docResult = new HashMap<>();
                docResult.put("id", doc.getId());
                docResult.put("success", false);
                docResult.put("error", e.getMessage());
                
                ((java.util.List) results.get("results")).add(docResult);
                failureCount++;
            }
        }
        
        results.put("successCount", successCount);
        results.put("failureCount", failureCount);
        
        long duration = System.currentTimeMillis() - start;
        
        log.info("Batch extraction completed: {} success, {} failure ({}ms)", 
                 successCount, failureCount, duration);
        
        return ApiResponse.success(results, MDC.get("correlationId"), duration);
    }

    /**
     * Validate template
     * 
     * POST /api/v1/validate-template
     * 
     * Validates a template without executing extraction
     */
    @PostMapping("/validate-template")
    @Operation(
        summary = "Validate extraction template",
        description = "Validate template structure and business rules without executing extraction"
    )
    public ApiResponse<ValidationResponse> validateTemplate(@Valid @RequestBody ValidateTemplateRequest request) {
        log.info("Template validation requested");
        
        long start = System.currentTimeMillis();
        
        // Validate template
        Map<String, Object> validationResult = validationService.validateTemplate(
            request.getTemplate(),
            request.getStrictMode() != null ? request.getStrictMode() : false
        );
        
        // Build response
        ValidationResponse response = ValidationResponse.builder()
            .valid((Boolean) validationResult.get("valid"))
            .template((ValidationResponse.TemplateSummary) validationResult.get("summary"))
            .validations((java.util.List<ValidationResponse.ValidationIssue>) validationResult.get("issues"))
            .build();
        
        long duration = System.currentTimeMillis() - start;
        
        log.info("Template validation completed: {} ({}ms)", 
                 response.isValid() ? "VALID" : "INVALID", duration);
        
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }

    /**
     * Helper method to build JobResponse from Job entity
     */
    private JobResponse buildJobResponse(Job job) {
        return JobResponse.builder()
            .jobId(job.getId())
            .status(job.getStatus().name())
            .progress(job.getProgress())
            .createdAt(job.getCreatedAt())
            .startedAt(job.getStartedAt())
            .completedAt(job.getCompletedAt())
            .estimatedCompletion(calculateEstimatedCompletion(job))
            .duration(job.getDurationMs())
            .statusUrl("/api/v1/extract/async/" + job.getId())
            .resultUrl("/api/v1/extract/async/" + job.getId() + "/result")
            .result(job.getResultPayload())
            .errorMessage(job.getErrorMessage())
            .build();
    }

    /**
     * Calculate estimated completion time for job
     */
    private java.time.Instant calculateEstimatedCompletion(Job job) {
        if (job.getStatus() == com.atina.invoice.api.model.JobStatus.COMPLETED ||
            job.getStatus() == com.atina.invoice.api.model.JobStatus.FAILED ||
            job.getStatus() == com.atina.invoice.api.model.JobStatus.CANCELLED) {
            return null;
        }
        
        if (job.getStartedAt() == null) {
            // Not started yet, estimate based on average
            return java.time.Instant.now().plusSeconds(30);
        }
        
        // Estimate based on progress
        long elapsed = System.currentTimeMillis() - job.getStartedAt().toEpochMilli();
        int progress = job.getProgress() != null ? job.getProgress() : 0;
        
        if (progress > 0 && progress < 100) {
            long totalEstimated = (elapsed * 100) / progress;
            long remaining = totalEstimated - elapsed;
            return java.time.Instant.now().plusMillis(remaining);
        }
        
        return java.time.Instant.now().plusSeconds(30);
    }
}

// ============================================
// USAGE EXAMPLES
// ============================================

/*
1. SYNCHRONOUS EXTRACTION

Request:
POST /api/v1/extract
Authorization: Bearer {token}
Content-Type: application/json

{
  "docling": { ...docling json... },
  "template": { ...template json... },
  "options": {
    "includeMeta": true,
    "includeEvidence": false
  }
}

Response:
{
  "success": true,
  "correlationId": "api-20240110-123456-a1b2c3d4",
  "duration": 2500,
  "data": {
    "invoice": {
      "number": "001-12345",
      "date": "2024-01-10",
      "total": 1234.56
    },
    "_meta": {...},
    "validations": []
  }
}

2. ASYNCHRONOUS EXTRACTION

Request:
POST /api/v1/extract/async
Authorization: Bearer {token}

{
  "docling": {...},
  "template": {...}
}

Response:
{
  "success": true,
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "progress": 0,
    "createdAt": "2024-01-10T12:34:56.789Z",
    "statusUrl": "/api/v1/extract/async/550e8400-...",
    "resultUrl": "/api/v1/extract/async/550e8400-.../result"
  }
}

3. GET JOB STATUS

Request:
GET /api/v1/extract/async/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "progress": 75,
    "createdAt": "2024-01-10T12:34:56.789Z",
    "startedAt": "2024-01-10T12:34:57.000Z",
    "estimatedCompletion": "2024-01-10T12:35:02.000Z",
    "statusUrl": "...",
    "resultUrl": "..."
  }
}

4. GET JOB RESULT

Request:
GET /api/v1/extract/async/550e8400-e29b-41d4-a716-446655440000/result
Authorization: Bearer {token}

Response:
{
  "success": true,
  "data": {
    "invoice": {...},
    "_meta": {...}
  }
}

5. BATCH EXTRACTION

Request:
POST /api/v1/extract/batch
Authorization: Bearer {token}

{
  "template": {...},
  "documents": [
    {
      "id": "doc1",
      "docling": {...}
    },
    {
      "id": "doc2",
      "docling": {...}
    }
  ],
  "options": {
    "includeMeta": false
  }
}

Response:
{
  "success": true,
  "data": {
    "totalDocuments": 2,
    "successCount": 2,
    "failureCount": 0,
    "results": [
      {
        "id": "doc1",
        "success": true,
        "data": {...}
      },
      {
        "id": "doc2",
        "success": true,
        "data": {...}
      }
    ]
  }
}

6. VALIDATE TEMPLATE

Request:
POST /api/v1/validate-template
Authorization: Bearer {token}

{
  "template": {...},
  "strictMode": true
}

Response:
{
  "success": true,
  "data": {
    "valid": true,
    "template": {
      "templateId": "FACTURA_AR_V1",
      "blocksCount": 2,
      "rulesCount": 5,
      "ruleTypes": {
        "anchor_proximity": 3,
        "line_regex": 2
      }
    },
    "validations": []
  }
}

7. CURL EXAMPLES

# Sync extraction
curl -X POST http://localhost:8080/api/v1/extract \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @extraction-request.json

# Async extraction
curl -X POST http://localhost:8080/api/v1/extract/async \
  -H "Authorization: Bearer $TOKEN" \
  -d @extraction-request.json \
  | jq -r '.data.jobId'

# Poll job status
JOB_ID="550e8400-e29b-41d4-a716-446655440000"
curl http://localhost:8080/api/v1/extract/async/$JOB_ID \
  -H "Authorization: Bearer $TOKEN"

# Get result when complete
curl http://localhost:8080/api/v1/extract/async/$JOB_ID/result \
  -H "Authorization: Bearer $TOKEN"

# Validate template
curl -X POST http://localhost:8080/api/v1/validate-template \
  -H "Authorization: Bearer $TOKEN" \
  -d @template.json
*/
