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

import java.io.File;
import java.io.IOException;
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
 * Modificado para:
 * - Async REAL (no bloquea endpoint)
 * - Usa pdfStorageId para guardar referencias temporales
 * - Limpieza automática de archivos temporales
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ExtractionService extractionService;
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
     * Crea job con storage temporal (NUEVO - async real)
     *
     * @param storageId ID del storage temporal donde están los inputs
     * @param inputType Tipo de input (JSON/FILE/PATH)
     * @param options Opciones de extracción
     * @param correlationId ID de correlación
     * @return Job creado
     */
    @Transactional
    public Job createJobWithStorage(
            String storageId,
            String inputType,
            ExtractionOptions options,
            String correlationId
    ) throws JsonProcessingException {

        // Serializar solo las opciones (no todo el docling/template)
        String optionsJson = options != null ?
                objectMapper.writeValueAsString(options) : null;

        Job job = Job.builder()
                .status(JobStatus.PENDING)
                .correlationId(correlationId)
                .storageId(storageId)  // ✅ Usar campo storage genérico
                .requestPayload(optionsJson)  // Solo opciones
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
     * @deprecated Se mantiene por compatibilidad, pero se recomienda usar processJobAsync
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
            // Update status to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(Instant.now());
            job.setProgress(10);
            jobRepository.save(job);

            // Parse request payload (método viejo)
            Map<String, Object> payload = objectMapper.readValue(job.getRequestPayload(), Map.class);
            JsonNode docling = objectMapper.valueToTree(payload.get("docling"));
            JsonNode template = objectMapper.valueToTree(payload.get("template"));

            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) payload.get("options");
            ExtractionOptions options = objectMapper.convertValue(optionsMap, ExtractionOptions.class);

            job.setProgress(30);
            jobRepository.save(job);

            // Perform extraction
            log.info("Executing extraction for job {}", jobId);
            JsonNode result = extractionService.extract(docling, template, options);

            job.setProgress(90);
            jobRepository.save(job);

            // Save result
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
     * Procesa job de forma asíncrona (ASYNC REAL)
     *
     * Este método NO bloquea - se ejecuta en thread pool separado.
     *
     * @param jobId ID del job a procesar
     * @return CompletableFuture que se completa cuando termina
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

            // 2. Recuperar inputs desde storage temporal
            String storageId = job.getStorageId();
            Path storagePath = Paths.get("/tmp/invoice-extractor", storageId);

            log.debug("Job {}: Loading inputs from storage {}", jobId, storageId);

            // Verificar si es PATH reference o archivos directos
            JsonNode docling;
            JsonNode template;

            Path pathsFile = storagePath.resolve("paths.txt");
            if (Files.exists(pathsFile)) {
                // Modo PATH: leer referencias
                String content = Files.readString(pathsFile);
                String[] lines = content.split("\n");
                String doclingPath = lines[0].substring("docling=".length());
                String templatePath = lines[1].substring("template=".length());

                docling = objectMapper.readTree(new File(doclingPath));
                template = objectMapper.readTree(new File(templatePath));
            } else {
                // Modo JSON o FILE: leer archivos guardados
                docling = objectMapper.readTree(storagePath.resolve("docling.json").toFile());
                template = objectMapper.readTree(storagePath.resolve("template.json").toFile());
            }

            job.setProgress(30);
            jobRepository.save(job);

            // 3. Parsear opciones
            ExtractionOptions options = null;
            if (job.getRequestPayload() != null && !job.getRequestPayload().isEmpty()) {
                options = objectMapper.readValue(job.getRequestPayload(), ExtractionOptions.class);
            }

            job.setProgress(40);
            jobRepository.save(job);

            // 4. Ejecutar extracción
            log.debug("Job {}: Extracting data", jobId);
            JsonNode result = extractionService.extract(docling, template, options);

            job.setProgress(90);
            jobRepository.save(job);

            // 5. Guardar resultado
            String resultJson = objectMapper.writeValueAsString(result);
            job.setResultPayload(resultJson);
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setProgress(100);
            job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));

            jobRepository.save(job);

            // 6. Limpiar storage temporal
            cleanupStorage(storagePath);

            log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);

            // Actualizar job con error
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(truncateError(e.getMessage()));

            if (job.getStartedAt() != null) {
                job.setDurationMs(ChronoUnit.MILLIS.between(job.getStartedAt(), job.getCompletedAt()));
            }

            jobRepository.save(job);

            // Intentar limpiar storage temporal incluso en error
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
                        .sorted((a, b) -> -a.compareTo(b)) // Orden inverso
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
}
