package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.*;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.JobResponse;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.service.DoclingService;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Controller unificado para extracción
 *
 * Soporta todas las combinaciones:
 * - PDF: File o Path
 * - Template: JSON (texto), File o Path
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
    private final DoclingService doclingService;
    private final ObjectMapper objectMapper;

    /**
     * Extracción síncrona (UNIFICADA - FLEXIBLE)
     *
     * POST /api/v1/extract
     *
     * PDF (Docling):
     * - doclingFile: PDF como archivo
     * - doclingPath: Path al PDF
     *
     * Template:
     * - template: JSON directo (texto)
     * - templateFile: Archivo JSON
     * - templatePath: Path al archivo
     *
     * Todas las combinaciones son válidas:
     * - PDF File + Template JSON
     * - PDF File + Template File
     * - PDF File + Template Path
     * - PDF Path + Template JSON
     * - PDF Path + Template File
     * - PDF Path + Template Path
     */
    @PostMapping(consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE
    })
    @Operation(
            summary = "Extract data (synchronous)",
            description = """
                    Extract invoice data from PDF and template.
                    
                    PDF Input (choose one):
                    - doclingFile: Upload PDF file
                    - doclingPath: Path to PDF in filesystem
                    
                    Template Input (choose one):
                    - template: JSON object directly
                    - templateFile: Upload template file
                    - templatePath: Path to template in filesystem
                    
                    All combinations are supported.
                    Returns result immediately (5-15 seconds for PDF conversion).
                    """
    )
    public ApiResponse<JsonNode> extract(
            // PDF inputs
            @RequestPart(value = "doclingFile", required = false) MultipartFile doclingFile,
            @RequestPart(value = "doclingPath", required = false) String doclingPath,

            // Template inputs
            @RequestPart(value = "template", required = false) String templateJson,
            @RequestPart(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestPart(value = "templatePath", required = false) String templatePath,

            // Options
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("Sync extraction requested");

        long start = System.currentTimeMillis();

        try {
            // 1. Parsear options
            ExtractionOptions options = parseOptions(optionsJson);

            // 2. Procesar PDF → Docling JSON
            JsonNode docling = processDocling(doclingFile, doclingPath);

            // 3. Procesar Template
            JsonNode template = processTemplate(templateJson, templateFile, templatePath);

            // 4. Extraer datos
            JsonNode result = extractionService.extract(docling, template, options);

            long duration = System.currentTimeMillis() - start;

            log.info("Sync extraction completed in {}ms", duration);

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
     * Extracción asíncrona (ASYNC REAL - FLEXIBLE)
     *
     * POST /api/v1/extract/async
     *
     * Soporta las mismas combinaciones que sync.
     * Retorna inmediatamente con jobId.
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
                    
                    Supports same input combinations as sync endpoint.
                    Returns immediately (~50-200ms) with job ID.
                    Processing happens in background.
                    
                    Use GET /extract/async/{jobId} to check status and get result.
                    """
    )
    public ApiResponse<JobResponse> extractAsync(
            // PDF inputs
            @RequestPart(value = "doclingFile", required = false) MultipartFile doclingFile,
            @RequestPart(value = "doclingPath", required = false) String doclingPath,

            // Template inputs
            @RequestPart(value = "template", required = false) String templateJson,
            @RequestPart(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestPart(value = "templatePath", required = false) String templatePath,

            // Options
            @RequestPart(value = "options", required = false) String optionsJson
    ) {
        log.info("Async extraction requested");

        long start = System.currentTimeMillis();

        try {
            // 1. Parsear options
            ExtractionOptions options = parseOptions(optionsJson);

            // 2. Guardar inputs temporalmente SIN procesarlos
            String storageId = saveInputsTemporarily(
                    doclingFile, doclingPath,
                    templateJson, templateFile, templatePath
            );

            // 3. Crear job
            String inputType = detectInputType(doclingFile, doclingPath);
            Job job = jobService.createJobWithStorage(
                    storageId,
                    inputType,
                    options,
                    MDC.get("correlationId")
            );

            // 4. Procesar async - NO ESPERA
            jobService.processJobAsync(job.getId());

            // 5. Retornar inmediatamente
            JobResponse response = buildJobResponse(job);

            long duration = System.currentTimeMillis() - start;

            log.info("Async job created: {} ({}ms)", job.getId(), duration);

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
     */
    @GetMapping("/async/{jobId}")
    @Operation(
            summary = "Get job status and result",
            description = "Get current status of async extraction job. Includes result when completed."
    )
    public ApiResponse<JobResponse> getJob(@PathVariable String jobId) {
        log.info("Job status requested: {}", jobId);

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

    /**
     * Obtener solo resultado del job (DEPRECATED)
     */
    @GetMapping("/async/{jobId}/result")
    @Deprecated
    @Operation(
            summary = "Get job result (deprecated)",
            description = "Deprecated: Use GET /async/{jobId} instead."
    )
    public ApiResponse<JsonNode> getJobResult(@PathVariable String jobId) {
        log.info("Job result requested: {}", jobId);

        long start = System.currentTimeMillis();

        try {
            JsonNode result = jobService.getJobResult(jobId);

            long duration = System.currentTimeMillis() - start;

            return ApiResponse.success(result, MDC.get("correlationId"), duration);

        } catch (Exception e) {
            log.error("Failed to get job result: {}", jobId, e);
            long duration = System.currentTimeMillis() - start;
            return ApiResponse.error(
                    "Failed to get job result: " + e.getMessage(),
                    MDC.get("correlationId"),
                    duration
            );
        }
    }

    // ============================================================
    // PROCESSING METHODS - FLEXIBLE
    // ============================================================

    /**
     * Procesa PDF (de File o Path) y convierte a Docling JSON
     *
     * @param doclingFile PDF como archivo (opcional)
     * @param doclingPath Path al PDF (opcional)
     * @return Docling JSON
     * @throws IOException si hay error de lectura/conversión
     */
    private JsonNode processDocling(MultipartFile doclingFile, String doclingPath)
            throws IOException {

        if (doclingFile != null && !doclingFile.isEmpty()) {
            // Opción 1: PDF como File
            log.info("Processing PDF from file: {}", doclingFile.getOriginalFilename());

            if (isPdf(doclingFile.getOriginalFilename())) {
                log.info("Converting PDF file to Docling JSON");
                return doclingService.convertPdf(doclingFile);
            } else {
                throw new IllegalArgumentException(
                        "doclingFile must be a PDF. Received: " + doclingFile.getOriginalFilename()
                );
            }

        } else if (doclingPath != null && !doclingPath.isBlank()) {
            // Opción 2: PDF como Path
            doclingPath = doclingPath.trim();
            log.info("Processing PDF from path: {}", doclingPath);

            boolean isPdf = isPdf(doclingPath);

            log.info("Processing PDF. is PDF? : {}", isPdf);

            if (isPdf) {
                log.info("Converting PDF from path to Docling JSON");

                // Leer PDF y crear MultipartFile
                byte[] pdfBytes = Files.readAllBytes(Paths.get(doclingPath));
                MultipartFile pdfFile = new ByteArrayMultipartFile(
                        "file",
                        new File(doclingPath).getName(),
                        "application/pdf",
                        pdfBytes
                );

                return doclingService.convertPdf(pdfFile);
            } else {
                throw new IllegalArgumentException(
                        "doclingPath must point to a PDF file. Received: " + doclingPath
                );
            }

        } else {
            throw new IllegalArgumentException(
                    "No PDF input provided. Must provide either doclingFile or doclingPath"
            );
        }
    }

    /**
     * Procesa Template (de JSON, File o Path)
     *
     * @param templateJson Template como JSON texto (opcional)
     * @param templateFile Template como archivo (opcional)
     * @param templatePath Path al template (opcional)
     * @return Template JSON
     * @throws IOException si hay error de lectura
     */
    private JsonNode processTemplate(String templateJson, MultipartFile templateFile,
                                     String templatePath) throws IOException {

        if (templateJson != null && !templateJson.isBlank()) {
            // Opción 1: Template como JSON texto
            log.info("Processing template from JSON text");
            return objectMapper.readTree(templateJson);

        } else if (templateFile != null && !templateFile.isEmpty()) {
            // Opción 2: Template como File
            log.info("Processing template from file: {}", templateFile.getOriginalFilename());
            return objectMapper.readTree(templateFile.getInputStream());

        } else if (templatePath != null && !templatePath.isBlank()) {
            // Opción 3: Template como Path
            log.info("Processing template from path: {}", templatePath);
            return objectMapper.readTree(new File(templatePath));

        } else {
            throw new IllegalArgumentException(
                    "No template input provided. Must provide one of: template, templateFile, or templatePath"
            );
        }
    }

    /**
     * Guarda inputs temporalmente para async (sin procesar)
     */
    private String saveInputsTemporarily(
            MultipartFile doclingFile, String doclingPath,
            String templateJson, MultipartFile templateFile, String templatePath
    ) throws IOException {

        String storageId = UUID.randomUUID().toString();
        Path storagePath = Paths.get("/tmp/invoice-extractor", storageId);
        Files.createDirectories(storagePath);

        // Guardar PDF
        if (doclingFile != null && !doclingFile.isEmpty()) {
            // PDF como archivo
            Files.copy(
                    doclingFile.getInputStream(),
                    storagePath.resolve("docling.pdf")
            );
        } else if (doclingPath != null && !doclingPath.isBlank()) {
            // PDF como path - guardar referencia
            Files.writeString(
                    storagePath.resolve("docling-path.txt"),
                    doclingPath
            );
        }

        // Guardar Template
        if (templateJson != null && !templateJson.isBlank()) {
            // Template como JSON texto
            Files.writeString(
                    storagePath.resolve("template.json"),
                    templateJson
            );
        } else if (templateFile != null && !templateFile.isEmpty()) {
            // Template como archivo
            Files.copy(
                    templateFile.getInputStream(),
                    storagePath.resolve("template.json")
            );
        } else if (templatePath != null && !templatePath.isBlank()) {
            // Template como path - guardar referencia
            Files.writeString(
                    storagePath.resolve("template-path.txt"),
                    templatePath
            );
        }

        log.info("Saved inputs temporarily: {}", storageId);

        return storageId;
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Parsea options desde JSON string
     */
    private ExtractionOptions parseOptions(String optionsJson) throws IOException {
        if (optionsJson == null || optionsJson.isEmpty() || optionsJson.equals("{}")) {
            return new ExtractionOptions();
        }

        return objectMapper.readValue(optionsJson, ExtractionOptions.class);
    }

    /**
     * Detecta tipo de input
     */
    private String detectInputType(MultipartFile doclingFile, String doclingPath) {
        if (doclingFile != null) return "PDF_FILE";
        if (doclingPath != null) return "PDF_PATH";
        return "UNKNOWN";
    }

    /**
     * Verifica si el nombre/path es PDF
     */
    private boolean isPdf(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
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

    /**
     * Implementación simple de MultipartFile para uso interno
     */
    private static class ByteArrayMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public ByteArrayMultipartFile(String name, String originalFilename,
                                      String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
