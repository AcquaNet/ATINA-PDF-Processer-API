package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.*;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.JobResponse;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.service.ExtractionService;
import com.atina.invoice.api.service.JobService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Controller unificado para extracción
 *
 * Refactorizado para:
 * - Un endpoint soporta múltiples formatos (JSON/File/Path)
 * - Async REAL (<100ms response)
 * - Sin duplicación de código
 *
 * @author Atina Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/extract")
@RequiredArgsConstructor
@Tag(name = "Extraction", description = "Unified extraction endpoints")
public class ExtractionController {

    private final ExtractionService extractionService;
    private final JobService jobService;
    private final ObjectMapper objectMapper;

    /**
     * Extracción síncrona (UNIFICADA)
     *
     * POST /api/v1/extract
     *
     * Soporta 3 formatos de input:
     * 1. JSON en body (application/json)
     * 2. Files (multipart/form-data)
     * 3. Paths en filesystem (application/json con paths)
     *
     * Retorna el resultado inmediatamente.
     */
    @PostMapping(consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE
    })
    @Operation(
            summary = "Extract data (synchronous)",
            description = """
                    Extract invoice data using docling and template.
                    
                    Supports 3 input formats:
                    1. JSON: Send docling and template as JSON objects
                    2. Files: Send docling and template as files
                    3. Paths: Send paths to files in shared filesystem
                    
                    Returns result immediately (1-5 seconds).
                    """
    )
    public ApiResponse<JsonNode> extract(@ModelAttribute ExtractionRequest request) {
        log.info("Sync extraction requested: type={}", request.getInputType());

        long start = System.currentTimeMillis();

        try {
            // 1. Convertir a formato estándar según tipo
            JsonNode docling = getDocling(request);
            JsonNode template = getTemplate(request);

            // 2. Extraer datos (lógica única)
            JsonNode result = extractionService.extract(
                    docling,
                    template,
                    request.getOptions()
            );

            long duration = System.currentTimeMillis() - start;

            log.info("Sync extraction completed in {}ms (type: {})",
                    duration, request.getInputType());

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Sync extraction failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    "Extraction failed: " + e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );
        }
    }

    /**
     * Extracción asíncrona (ASYNC REAL)
     *
     * POST /api/v1/extract/async
     *
     * Soporta los mismos 3 formatos que sync.
     *
     * Retorna inmediatamente (~50-100ms) con jobId.
     * El procesamiento ocurre en background.
     */
    @PostMapping(
            value = "/async",
            consumes = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.MULTIPART_FORM_DATA_VALUE
            }
    )
    @Operation(
            summary = "Extract data (asynchronous)",
            description = """
                    Extract invoice data asynchronously.
                    
                    Supports same 3 input formats as sync endpoint.
                    Returns immediately (~50-100ms) with job ID.
                    Processing happens in background.
                    
                    Use GET /extract/async/{jobId} to check status and get result.
                    """
    )
    public ApiResponse<JobResponse> extractAsync(@ModelAttribute ExtractionRequest request) {
        log.info("Async extraction requested: type={}", request.getInputType());

        long start = System.currentTimeMillis();

        try {
            // 1. Guardar input temporalmente SIN procesarlo (rápido: ~20-50ms)
            String storageId = saveInputTemporarily(request);

            // 2. Crear job (rápido: ~10ms)
            Job job = jobService.createJobWithStorage(
                    storageId,
                    request.getInputType().name(),
                    request.getOptions(),
                    MDC.get("correlationId")
            );

            // 3. Procesar async - NO ESPERA (rápido: ~5ms para lanzar)
            jobService.processJobAsync(job.getId());

            // 4. Retornar inmediatamente
            JobResponse response = buildJobResponse(job);

            long duration = System.currentTimeMillis() - start;

            log.info("Async job created: {} ({}ms, type: {})",
                    job.getId(), duration, request.getInputType());

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Async job creation failed", e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    "Job creation failed: " + e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );
        }
    }

    /**
     * Obtener estado y resultado del job
     *
     * GET /api/v1/extract/async/{jobId}
     */
    @GetMapping("/async/{jobId}")
    @Operation(
            summary = "Get job status and result",
            description = "Get current status of async extraction job. Includes result when completed."
    )
    public ApiResponse<JobResponse> getJob(@PathVariable String jobId) {
        log.debug("Job status requested: {}", jobId);

        long start = System.currentTimeMillis();

        try {
            Job job = jobService.getJob(jobId);
            JobResponse response = buildJobResponse(job);

            long duration = System.currentTimeMillis() - start;

            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Failed to get job: {}", jobId, e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    "Failed to get job: " + e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );
        }
    }
  
    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Obtiene docling según tipo de input
     */
    private JsonNode getDocling(ExtractionRequest request) throws IOException {
        return switch (request.getInputType()) {
            case JSON -> request.getDocling();
            case FILE -> objectMapper.readTree(request.getDoclingFile().getInputStream());
            case PATH -> objectMapper.readTree(new File(request.getDoclingPath()));
        };
    }

    /**
     * Obtiene template según tipo de input
     */
    private JsonNode getTemplate(ExtractionRequest request) throws IOException {
        return switch (request.getInputType()) {
            case JSON -> request.getTemplate();
            case FILE -> objectMapper.readTree(request.getTemplateFile().getInputStream());
            case PATH -> objectMapper.readTree(new File(request.getTemplatePath()));
        };
    }

    /**
     * Guarda input temporalmente para procesamiento async
     *
     * @return storageId
     */
    private String saveInputTemporarily(ExtractionRequest request) throws IOException {
        String storageId = UUID.randomUUID().toString();
        Path storagePath = Paths.get("/tmp/invoice-extractor", storageId);
        Files.createDirectories(storagePath);

        switch (request.getInputType()) {
            case JSON -> {
                // Guardar JSON como archivos
                objectMapper.writeValue(
                        storagePath.resolve("docling.json").toFile(),
                        request.getDocling()
                );
                objectMapper.writeValue(
                        storagePath.resolve("template.json").toFile(),
                        request.getTemplate()
                );
            }
            case FILE -> {
                // Copiar archivos
                Files.copy(
                        request.getDoclingFile().getInputStream(),
                        storagePath.resolve("docling.json")
                );
                Files.copy(
                        request.getTemplateFile().getInputStream(),
                        storagePath.resolve("template.json")
                );
            }
            case PATH -> {
                // Guardar referencias
                Files.writeString(
                        storagePath.resolve("paths.txt"),
                        "docling=" + request.getDoclingPath() + "\n" +
                                "template=" + request.getTemplatePath()
                );
            }
        }

        log.debug("Saved input temporarily: {} (type: {})", storageId, request.getInputType());

        return storageId;
    }

    /**
     * Construye JobResponse desde Job entity
     */
    private JobResponse buildJobResponse(Job job) {
        JobResponse.JobResponseBuilder builder = JobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .progress(job.getProgress())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .duration(job.getDurationMs())
                .statusUrl("/api/v1/extract/async/" + job.getId());

        // Incluir resultado si está completado
        if (job.getStatus() == JobStatus.COMPLETED && job.getResultPayload() != null) {
            try {
                builder.result(objectMapper.readTree(job.getResultPayload()));
            } catch (Exception e) {
                log.warn("Failed to parse job result for response", e);
            }
        }

        // Incluir error si falló
        if (job.getStatus() == JobStatus.FAILED) {
            builder.errorMessage(job.getErrorMessage());
        }

        return builder.build();
    }
}

