package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.*;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.JobResponse;
import com.atina.invoice.api.dto.response.ValidationResponse;
import com.atina.invoice.api.exception.DoclingException;
import com.atina.invoice.api.exception.ExtractionException;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.service.DoclingService;
import com.atina.invoice.api.service.ExtractionService;
import com.atina.invoice.api.service.JobService;
import com.atina.invoice.api.service.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;

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
    private final DoclingService doclingService;  // ⭐ AGREGAR
    private final ObjectMapper objectMapper;

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
     * Extract from PDF file
     *
     * POST /api/v1/extract/pdf
     *
     * Receives PDF, converts to Docling JSON, then extracts data
     * Combines PDF conversion + extraction in one step
     */
    @PostMapping(value = "/extract/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Extract invoice data from PDF",
            description = "Upload PDF and template. System converts PDF to Docling JSON automatically, then extracts data."
    )
    public ApiResponse<JsonNode> extractFromPdf(
            @RequestPart("file") MultipartFile pdfFile,
            @RequestPart("template") String templateJson,
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("PDF extraction requested: {} ({} bytes)",
                pdfFile.getOriginalFilename(), pdfFile.getSize());

        long start = System.currentTimeMillis();

        try {
            // 1. Parse template
            JsonNode template = objectMapper.readTree(templateJson);

            // 2. Parse options (if provided)
            ExtractionOptions options = null;
            if (optionsJson != null && !optionsJson.isEmpty()) {
                options = objectMapper.readValue(optionsJson, ExtractionOptions.class);
            }

            // 3. Convert PDF to Docling JSON
            log.debug("Converting PDF to Docling JSON...");
            JsonNode doclingJson = doclingService.convertPdf(pdfFile);
            log.debug("PDF converted successfully");

            // 4. Extract data using template
            log.debug("Extracting data from Docling JSON...");
            JsonNode result = extractionService.extract(doclingJson, template, options);
            log.debug("Extraction completed successfully");

            long duration = System.currentTimeMillis() - start;

            log.info("PDF extraction completed in {}ms", duration);

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (DoclingException e) {
            log.error("Docling conversion failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Docling conversion failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);

        } catch (ExtractionException e) {
            log.error("Extraction failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("PDF extraction failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("PDF extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
    }

    /**
     * Extract from PDF file (asynchronous)
     *
     * POST /api/v1/extract/pdf/async
     *
     * Creates job for PDF extraction in background
     */
    @PostMapping(value = "/extract/pdf/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Extract from PDF (async)",
            description = "Upload PDF and template. Creates job for background processing. Returns job ID immediately."
    )
    public ApiResponse<JobResponse> extractFromPdfAsync(
            @RequestPart("file") MultipartFile pdfFile,
            @RequestPart("template") String templateJson,
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("Async PDF extraction requested: {}", pdfFile.getOriginalFilename());

        long start = System.currentTimeMillis();

        try {
            // 1. Convert PDF to Docling JSON first (to avoid storing binary in job)
            log.debug("Converting PDF to Docling JSON...");
            JsonNode doclingJson = doclingService.convertPdf(pdfFile);

            // 2. Parse template
            JsonNode template = objectMapper.readTree(templateJson);

            // 3. Parse options
            ExtractionOptions options = null;
            if (optionsJson != null && !optionsJson.isEmpty()) {
                options = objectMapper.readValue(optionsJson, ExtractionOptions.class);
            }

            // 4. Create job with Docling JSON
            Job job = jobService.createJob(
                    doclingJson,
                    template,
                    options,
                    MDC.get("correlationId")
            );

            // 5. Process async
            jobService.processJobAsync(job.getId());

            // 6. Build response
            JobResponse response = buildJobResponse(job);

            long duration = System.currentTimeMillis() - start;

            log.info("Async PDF job created: {} ({}ms)", job.getId(), duration);

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Async PDF extraction failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Async PDF extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
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

        // Create job - FIXED: pasar los parámetros correctos
        Job job = jobService.createJob(
                request.getDocling(),
                request.getTemplate(),
                request.getOptions(),
                MDC.get("correlationId")
        );

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
    public ApiResponse<JsonNode> validateTemplate(@Valid @RequestBody ValidateTemplateRequest request) {

        log.info("Template validation requested");

        long start = System.currentTimeMillis();

        // Build response
        JsonNode result = validationService.validateTemplate(
                request.getTemplate(),
                request.getOptions()
        );

        long duration = System.currentTimeMillis() - start;


        return ApiResponse.success(result, MDC.get("correlationId"), duration);

    }

    /**
     * Validate template from file
     *
     * POST /api/v1/validate-template/file
     *
     * Validates a template file without executing extraction
     */
    @PostMapping(value = "/validate-template/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Validate extraction template from file",
            description = "Upload template file to validate structure and business rules without executing extraction"
    )
    public ApiResponse<JsonNode> validateTemplateFile(
            @RequestPart("template") MultipartFile templateFile,
            @RequestPart(value = "options", required = false) ValidateOptions options
    ) {
        log.info("Template file validation requested: {}", templateFile.getOriginalFilename());

        long start = System.currentTimeMillis();

        try {
            // Read and parse template file
            String templateContent = new String(templateFile.getBytes(), StandardCharsets.UTF_8);
            JsonNode template = objectMapper.readTree(templateContent);

            // Build response
            JsonNode result = validationService.validateTemplate(
                    template,
                    options
            );

            long duration = System.currentTimeMillis() - start;

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Template file validation failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Template validation failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
    }


    /**
     * Helper method to build JobResponse from Job entity
     */
    private JobResponse buildJobResponse(Job job) {
        return JobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())  // FIXED: usar .name() para convertir enum a String
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
        // FIXED: usar JobStatus enum correctamente
        if (job.getStatus() == JobStatus.COMPLETED ||
                job.getStatus() == JobStatus.FAILED ||
                job.getStatus() == JobStatus.CANCELLED) {
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


    /**
     * Extract from PDF file with template file
     *
     * POST /api/v1/extract/pdf/file
     *
     * Receives PDF and template as files (not JSON strings)
     * Combines PDF conversion + extraction in one step
     */
    @PostMapping(value = "/extract/pdf/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Extract invoice data from PDF with template file",
            description = "Upload PDF and template file. System converts PDF to Docling JSON automatically, then extracts data."
    )
    public ApiResponse<JsonNode> extractFromPdfWithFile(
            @RequestPart("pdf") MultipartFile pdfFile,
            @RequestPart("template") MultipartFile templateFile,
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("PDF extraction with file template requested: {} and {}",
                pdfFile.getOriginalFilename(), templateFile.getOriginalFilename());

        long start = System.currentTimeMillis();

        try {
            // 1. Read and parse template file
            log.debug("Reading template file...");
            String templateContent = new String(templateFile.getBytes(), StandardCharsets.UTF_8);
            JsonNode template = objectMapper.readTree(templateContent);
            log.debug("Template parsed successfully");

            // 2. Parse options (if provided)
            ExtractionOptions options = null;
            if (optionsJson != null && !optionsJson.isEmpty()) {
                options = objectMapper.readValue(optionsJson, ExtractionOptions.class);
            }

            // 3. Convert PDF to Docling JSON
            log.debug("Converting PDF to Docling JSON...");
            JsonNode doclingJson = doclingService.convertPdf(pdfFile);
            log.debug("PDF converted successfully");

            // 4. Extract data using template
            log.debug("Extracting data from Docling JSON...");
            JsonNode result = extractionService.extract(doclingJson, template, options);
            log.debug("Extraction completed successfully");

            long duration = System.currentTimeMillis() - start;

            log.info("PDF extraction with file completed in {}ms", duration);

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (DoclingException e) {
            log.error("Docling conversion failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Docling conversion failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);

        } catch (ExtractionException e) {
            log.error("Extraction failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("PDF extraction with file failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("PDF extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
    }

    /**
     * Extract from PDF file with template file (asynchronous)
     *
     * POST /api/v1/extract/pdf/file/async
     *
     * Creates job for PDF extraction with template file in background
     */
    @PostMapping(value = "/extract/pdf/file/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Extract from PDF with template file (async)",
            description = "Upload PDF and template file. Creates job for background processing. Returns job ID immediately."
    )
    public ApiResponse<JobResponse> extractFromPdfWithFileAsync(
            @RequestPart("pdf") MultipartFile pdfFile,
            @RequestPart("template") MultipartFile templateFile,
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("Async PDF extraction with file template requested: {} and {}",
                pdfFile.getOriginalFilename(), templateFile.getOriginalFilename());

        long start = System.currentTimeMillis();

        try {
            // 1. Read and parse template file
            log.debug("Reading template file...");
            String templateContent = new String(templateFile.getBytes(), StandardCharsets.UTF_8);
            JsonNode template = objectMapper.readTree(templateContent);
            log.debug("Template parsed successfully");

            // 2. Convert PDF to Docling JSON first
            log.debug("Converting PDF to Docling JSON...");
            JsonNode doclingJson = doclingService.convertPdf(pdfFile);

            // 3. Parse options
            ExtractionOptions options = null;
            if (optionsJson != null && !optionsJson.isEmpty()) {
                options = objectMapper.readValue(optionsJson, ExtractionOptions.class);
            }

            // 4. Create job with Docling JSON
            Job job = jobService.createJob(
                    doclingJson,
                    template,
                    options,
                    MDC.get("correlationId")
            );

            // 5. Process async
            jobService.processJobAsync(job.getId());

            // 6. Build response
            JobResponse response = buildJobResponse(job);

            long duration = System.currentTimeMillis() - start;

            log.info("Async PDF job with file created: {} ({}ms)", job.getId(), duration);

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Async PDF extraction with file failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error("Async PDF extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"), duration);
        }
    }

}
