package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.model.Job;
import com.atina.invoice.api.model.JobStatus;
import com.atina.invoice.api.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio para gestión de jobs asíncronos
 *
 * Modificado para soportar estructura flexible:
 * - PDF: File o Path
 * - Template: JSON, File o Path
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ExtractionService extractionService;
    private final DoclingService doclingService;
    private final ObjectMapper objectMapper;

    /**
     * Crea job para procesamiento async (MÉTODO VIEJO - mantener compatibilidad)
     *
     * @deprecated Usar createJobWithStorage para nuevos jobs
     */
    @Deprecated
    @Transactional
    public Job createJob(JsonNode docling, JsonNode template, ExtractionOptions options, String correlationId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("docling", docling);
            payload.put("template", template);
            payload.put("options", options);

            String payloadJson = objectMapper.writeValueAsString(payload);

            Job job = Job.builder()
                    .status(JobStatus.PENDING)
                    .correlationId(correlationId)
                    .requestPayload(payloadJson)
                    .progress(0)
                    .build();

            job = jobRepository.save(job);
            log.info("Created job {} with correlationId {}", job.getId(), correlationId);

            return job;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create job", e);
        }
    }

    /**
     * Crea job con storage temporal (NUEVO - async real con estructura flexible)
     */
    @Transactional
    public Job createJobWithStorage(
            String storageId,
            String inputType,
            ExtractionOptions options,
            String correlationId
    ) throws JsonProcessingException {

        String optionsJson = options != null ?
                objectMapper.writeValueAsString(options) : null;

        Job job = Job.builder()
                .status(JobStatus.PENDING)
                .correlationId(correlationId)
                .storageId(storageId)
                .requestPayload(optionsJson)
                .progress(0)
                .build();

        job = jobRepository.save(job);

        log.info("Created job {} with storage {} (type: {}, correlationId: {})",
                job.getId(), storageId, inputType, correlationId);

        return job;
    }

    /**
     * Procesa job de forma síncrona (VIEJO)
     *
     * @deprecated Se mantiene por compatibilidad
     */
    @Deprecated
    @Transactional
    public void processJob(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new com.atina.invoice.api.exception.JobNotFoundException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.PENDING) {
            log.warn("Job {} is not in PENDING status, current status: {}", jobId, job.getStatus());
            return;
        }

        try {
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(10);
            jobRepository.save(job);

            Map<String, Object> payload = objectMapper.readValue(job.getRequestPayload(), Map.class);
            JsonNode docling = objectMapper.valueToTree(payload.get("docling"));
            JsonNode template = objectMapper.valueToTree(payload.get("template"));

            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) payload.get("options");
            ExtractionOptions options = objectMapper.convertValue(optionsMap, ExtractionOptions.class);

            job.setProgress(30);
            jobRepository.save(job);

            log.info("Executing extraction for job {}", jobId);
            JsonNode result = extractionService.extract(docling, template, options);

            job.setProgress(90);
            jobRepository.save(job);

            String resultJson = objectMapper.writeValueAsString(result);
            job.setResultPayload(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setProgress(100);
            job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));

            jobRepository.save(job);

            log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());

        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);

            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(e.getMessage());

            if (job.getStartedAt() != null) {
                job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));
            }

            jobRepository.save(job);
        }
    }

    /**
     * Procesa job de forma asíncrona (ASYNC REAL con estructura flexible)
     *
     * Soporta:
     * - PDF: docling.pdf o docling-path.txt
     * - Template: template.json o template-path.txt
     */
    @Async("jobExecutor")
    public CompletableFuture<Void> processJobAsync(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        try {
            // 1. Actualizar estado a PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(10);
            jobRepository.save(job);

            log.info("Job {}: Started async processing", jobId);

            // 2. Recuperar storage path
            String storageId = job.getStorageId();
            Path storagePath = Paths.get("/tmp/invoice-extractor", storageId);

            log.debug("Job {}: Loading inputs from storage {}", jobId, storageId);

            // 3. Procesar PDF → Docling JSON
            JsonNode docling = processDoclingFromStorage(storagePath, jobId, job);

            // 4. Procesar Template
            JsonNode template = processTemplateFromStorage(storagePath, jobId, job);

            // 5. Parsear opciones
            ExtractionOptions options = null;
            if (job.getRequestPayload() != null && !job.getRequestPayload().isEmpty()) {
                options = objectMapper.readValue(job.getRequestPayload(), ExtractionOptions.class);
            }

            job.setProgress(50);
            jobRepository.save(job);

            // 6. Ejecutar extracción
            log.debug("Job {}: Extracting data", jobId);
            JsonNode result = extractionService.extract(docling, template, options);

            job.setProgress(90);
            jobRepository.save(job);

            // 7. Guardar resultado
            String resultJson = objectMapper.writeValueAsString(result);
            job.setResultPayload(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setProgress(100);
            job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));

            jobRepository.save(job);

            // 8. Limpiar storage temporal
            cleanupStorage(storagePath);

            log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);

            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(truncateError(e.getMessage()));

            if (job.getStartedAt() != null) {
                job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));
            }

            jobRepository.save(job);

            // Intentar limpiar storage temporal
            try {
                String storageId = job.getStorageId();
                if (storageId != null) {
                    Path storagePath = Paths.get("/tmp/invoice-extractor", storageId);
                    cleanupStorage(storagePath);
                }
            } catch (Exception cleanupError) {
                log.warn("Failed to cleanup storage after job failure", cleanupError);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Procesa PDF desde storage y convierte a Docling JSON
     *
     * Soporta:
     * - docling.pdf: PDF guardado directamente
     * - docling-path.txt: Path al PDF
     */
    private JsonNode processDoclingFromStorage(Path storagePath, String jobId, Job job)
            throws IOException {

        Path pdfFile = storagePath.resolve("docling.pdf");
        Path pdfPathFile = storagePath.resolve("docling-path.txt");

        if (Files.exists(pdfFile)) {
            // PDF guardado directamente
            log.info("Job {}: Converting PDF to Docling JSON", jobId);
            job.setProgress(20);
            jobRepository.save(job);

            byte[] pdfBytes = Files.readAllBytes(pdfFile);
            MultipartFile pdfMultipart = new ByteArrayMultipartFile(
                    "file",
                    "docling.pdf",
                    "application/pdf",
                    pdfBytes
            );

            JsonNode docling = doclingService.convertPdf(pdfMultipart);

            job.setProgress(35);
            jobRepository.save(job);

            return docling;

        } else if (Files.exists(pdfPathFile)) {
            // Path al PDF
            String pdfPath = Files.readString(pdfPathFile).trim();
            log.info("Job {}: Converting PDF from path {} to Docling JSON", jobId, pdfPath);

            job.setProgress(20);
            jobRepository.save(job);

            byte[] pdfBytes = Files.readAllBytes(Paths.get(pdfPath));
            MultipartFile pdfMultipart = new ByteArrayMultipartFile(
                    "file",
                    new File(pdfPath).getName(),
                    "application/pdf",
                    pdfBytes
            );

            JsonNode docling = doclingService.convertPdf(pdfMultipart);

            job.setProgress(35);
            jobRepository.save(job);

            return docling;

        } else {
            throw new IllegalStateException("No PDF input found in storage: " + storagePath);
        }
    }

    /**
     * Procesa Template desde storage
     *
     * Soporta:
     * - template.json: Template guardado directamente
     * - template-path.txt: Path al template
     */
    private JsonNode processTemplateFromStorage(Path storagePath, String jobId, Job job)
            throws IOException {

        Path templateFile = storagePath.resolve("template.json");
        Path templatePathFile = storagePath.resolve("template-path.txt");

        if (Files.exists(templateFile)) {
            // Template guardado directamente
            log.debug("Job {}: Reading template from storage", jobId);
            return objectMapper.readTree(templateFile.toFile());

        } else if (Files.exists(templatePathFile)) {
            // Path al template
            String templatePath = Files.readString(templatePathFile).trim();
            log.debug("Job {}: Reading template from path {}", jobId, templatePath);
            return objectMapper.readTree(new File(templatePath));

        } else {
            throw new IllegalStateException("No template input found in storage: " + storagePath);
        }
    }

    /**
     * Obtiene job por ID
     */
    public Job getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new com.atina.invoice.api.exception.JobNotFoundException(
                        "Job not found: " + jobId));
    }

    /**
     * Obtiene resultado del job
     */
    public JsonNode getJobResult(String jobId) {
        Job job = getJob(jobId);

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Job is not completed yet. Current status: " + job.getStatus());
        }

        try {
            return objectMapper.readTree(job.getResultPayload());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse job result", e);
        }
    }

    /**
     * Limpia storage temporal
     */
    private void cleanupStorage(Path storagePath) {
        try {
            if (Files.exists(storagePath)) {
                Files.walk(storagePath)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete file: {}", path, e);
                            }
                        });

                log.debug("Cleaned up storage: {}", storagePath);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup storage: {}", storagePath, e);
        }
    }

    /**
     * Trunca mensaje de error para DB
     */
    private String truncateError(String error) {
        if (error == null) {
            return null;
        }

        int maxLength = 4500;
        return error.length() > maxLength ?
                error.substring(0, maxLength) + "... (truncated)" :
                error;
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
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
