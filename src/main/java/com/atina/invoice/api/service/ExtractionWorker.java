package com.atina.invoice.api.service;

import com.atina.invoice.api.config.ExtractionProperties;
import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.model.ExtractionTask;
import com.atina.invoice.api.model.ExtractionTemplate;
import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.repository.ExtractionTaskRepository;
import com.atina.invoice.api.repository.ExtractionTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

/**
 * Worker asíncrono para procesar tareas de extracción de PDFs
 *
 * Este componente:
 * 1. Cada X segundos busca tareas PENDING o RETRYING
 * 2. Procesa cada tarea:
 *    - Busca template para (tenant, source)
 *    - Convierte PDF a JSON (DoclingService)
 *    - Extrae datos (ExtractionService)
 *    - Guarda resultado
 * 3. Notifica cuando todas las tareas de un email completan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionWorker {

    private final ExtractionTaskRepository taskRepository;
    private final ExtractionTemplateRepository templateRepository;
    private final DoclingService doclingService;
    private final ExtractionService extractionService;
    private final WebhookService webhookService;
    private final EmailNotificationService emailNotificationService;
    private final ExtractionProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Worker principal - ejecuta cada X segundos
     * Configurado vía extraction.worker.poll-interval-ms (default: 5000ms)
     */
    @Scheduled(fixedDelayString = "${extraction.worker.poll-interval-ms:5000}")
    public void processExtractionTasks() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }

        List<ExtractionTask> tasks = taskRepository.findNextTasksToProcess(Instant.now());

        if (tasks.isEmpty()) {
            return;
        }

        log.info("🔄 Found {} tasks to process", tasks.size());

        int batchSize = properties.getWorker().getBatchSize();
        int processed = 0;

        for (ExtractionTask task : tasks) {
            if (processed >= batchSize) {
                log.debug("Batch size limit reached ({}), stopping", batchSize);
                break;
            }

            try {
                processTask(task);
                processed++;
            } catch (Exception e) {
                log.error("Error processing task {}: {}", task.getId(), e.getMessage(), e);
                handleError(task, e);
            }
        }

        if (processed > 0) {
            log.info("✅ Processed {} tasks", processed);
        }
    }

    /**
     * Procesar una tarea de extracción
     */
    @Transactional
    protected void processTask(ExtractionTask task) {
        log.info("🔄 [TASK-{}] Processing extraction task for PDF: {}",
                task.getId(), task.getPdfPath());

        // 1. Mark as processing
        task.markAsProcessing();
        task = taskRepository.save(task);

        try {
            // 2. Buscar template para (tenant, source)
            Long tenantId = task.getEmail().getTenant().getId();
            String source = task.getSource();

            log.debug("[TASK-{}] Looking for template: tenant={}, source={}",
                    task.getId(), tenantId, source);

            ExtractionTemplate templateConfig = templateRepository
                    .findByTenantIdAndSourceAndIsActive(tenantId, source, true)
                    .orElseThrow(() -> new RuntimeException(
                            String.format("No active template found for tenant=%d, source=%s",
                                    tenantId, source)
                    ));

            log.info("[TASK-{}] Found template: {} (name: {}, fullPath: {})",
                    task.getId(), templateConfig.getDescription(),
                    templateConfig.getTemplateName(), templateConfig.getFullTemplatePath());

            // 3. Cargar PDF como MultipartFile
            File pdfFile = new File(task.getPdfPath());
            if (!pdfFile.exists()) {
                throw new FileNotFoundException("PDF file not found: " + task.getPdfPath());
            }

            log.debug("[TASK-{}] Loading PDF file: {} ({} bytes)",
                    task.getId(), pdfFile.getName(), pdfFile.length());

            MultipartFile multipartFile = convertFileToMultipartFile(pdfFile);

            // 4. PDF → JSON interno (usando DoclingService)
            log.info("[TASK-{}] Converting PDF to internal format...", task.getId());
            JsonNode processedData = doclingService.convertPdf(multipartFile);

            // 5. Cargar template desde filesystem
            String fullTemplatePath = templateConfig.getFullTemplatePath();
            log.debug("[TASK-{}] Loading template from: {}",
                    task.getId(), fullTemplatePath);

            File templateFile = new File(fullTemplatePath);
            if (!templateFile.exists()) {
                throw new FileNotFoundException(
                        "Template file not found: " + fullTemplatePath
                );
            }

            JsonNode template = objectMapper.readTree(templateFile);

            // 6. Extraer datos (usando ExtractionService)
            log.info("[TASK-{}] Extracting data from PDF...", task.getId());

            ExtractionOptions options = new ExtractionOptions();
            options.setIncludeMeta(true);
            options.setIncludeEvidence(false);
            options.setFailOnValidation(false);

            JsonNode result = extractionService.extract(processedData, template, options);

            // 7. Guardar resultado JSON
            String resultJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);
            String resultPath = saveExtractionResult(task, resultJson);

            log.info("[TASK-{}] Extraction result saved to: {}", task.getId(), resultPath);

            // 8. Actualizar tarea como completada
            task.markAsCompleted(resultPath, resultJson);
            taskRepository.save(task);

            log.info("✅ [TASK-{}] Extraction completed successfully", task.getId());

            // 9. Verificar si email está completamente procesado
            checkEmailCompletion(task.getEmail());

        } catch (Exception e) {
            log.error("❌ [TASK-{}] Extraction failed: {}", task.getId(), e.getMessage(), e);

            int retryDelay = calculateRetryDelay(task.getAttempts());
            task.markForRetry(e.getMessage(), retryDelay);
            taskRepository.save(task);

            log.warn("[TASK-{}] Marked for retry (attempts: {}/{}, next retry in {}s)",
                    task.getId(), task.getAttempts(), task.getMaxAttempts(), retryDelay);

            // Si ya no se reintentará más, verificar completion del email
            if (task.getStatus() == ExtractionStatus.FAILED) {
                log.error("[TASK-{}] Max attempts exceeded, marked as FAILED", task.getId());
                checkEmailCompletion(task.getEmail());
            }
        }
    }

    /**
     * Convertir File a MultipartFile
     * Basado en ExtractionController.ByteArrayMultipartFile
     */
    private MultipartFile convertFileToMultipartFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new ByteArrayMultipartFile(
                "file",
                file.getName(),
                "application/pdf",
                bytes
        );
    }

    /**
     * Guardar resultado de extracción en filesystem
     *
     * Path: {tenant_storage_base}/{tenant_code}/process/extractions/{email_id}_{attachment_id}_extraction.json
     */
    private String saveExtractionResult(ExtractionTask task, String resultJson) throws IOException {
        Tenant tenant = task.getEmail().getTenant();
        String basePath = tenant.getStorageBasePath();

        // Crear directorio: /tenant/process/extractions/
        Path directory = Paths.get(basePath, tenant.getTenantCode(), "process", "extractions");
        Files.createDirectories(directory);

        // Nombre archivo: {email_id}_{attachment_id}_extraction.json
        String filename = String.format("%d_%d_extraction.json",
                task.getEmail().getId(),
                task.getAttachment().getId());

        Path filePath = directory.resolve(filename);
        Files.writeString(filePath, resultJson);

        log.debug("Saved extraction result to: {}", filePath);

        return filePath.toString();
    }

    /**
     * Verificar si email está completamente procesado
     * Si sí, enviar webhook y notificación email
     */
    @Transactional
    protected void checkEmailCompletion(ProcessedEmail email) {
        List<ExtractionTask> tasks = taskRepository
                .findByEmailIdOrderByCreatedAtAsc(email.getId());

        boolean allDone = tasks.stream().allMatch(ExtractionTask::isTerminal);

        if (!allDone) {
            log.debug("[EMAIL-{}] Not all tasks completed yet", email.getId());
            return;
        }

        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();

        log.info("✅ [EMAIL-{}] All extraction tasks completed: {}/{} successful, {} failed",
                email.getId(), completed, tasks.size(), failed);

        // Enviar webhook si está configurado
        if (properties.getWebhook().isEnabled() &&
            email.getTenant().getWebhookUrl() != null &&
            !email.getTenant().getWebhookUrl().isBlank()) {

            try {
                log.info("[EMAIL-{}] Sending webhook notification", email.getId());
                webhookService.sendExtractionCompletedWebhook(email, tasks);
            } catch (Exception e) {
                log.error("[EMAIL-{}] Failed to send webhook: {}",
                        email.getId(), e.getMessage(), e);
            }
        }

        // Enviar email de procesamiento completado si está configurado
        if (email.getSenderRule() != null &&
            email.getSenderRule().getProcessEnabled() &&
            email.getSenderRule().getTemplateEmailProcessed() != null) {

            try {
                log.info("[EMAIL-{}] Sending processed email notification", email.getId());
                emailNotificationService.sendProcessedEmail(email, tasks);
            } catch (Exception e) {
                log.error("[EMAIL-{}] Failed to send email notification: {}",
                        email.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Calcular delay para reintento con exponential backoff
     */
    private int calculateRetryDelay(int attempts) {
        int baseDelay = properties.getWorker().getRetryDelaySeconds();
        return baseDelay * (int) Math.pow(2, attempts - 1);
    }

    /**
     * Manejar error en procesamiento de tarea
     */
    private void handleError(ExtractionTask task, Exception e) {
        try {
            task = taskRepository.findById(task.getId()).orElse(task);

            if (task.getStatus() == ExtractionStatus.PROCESSING) {
                int retryDelay = calculateRetryDelay(task.getAttempts());
                task.markForRetry("Worker error: " + e.getMessage(), retryDelay);
                taskRepository.save(task);
            }
        } catch (Exception ex) {
            log.error("Failed to handle error for task {}", task.getId(), ex);
        }
    }

    /**
     * Recuperar tareas atascadas (scheduled cada hora)
     * Tareas en PROCESSING por más de X minutos se marcan para retry
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void recoverStuckTasks() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }

        int thresholdMinutes = properties.getWorker().getStuckTaskThresholdMinutes();
        Instant threshold = Instant.now().minusSeconds(thresholdMinutes * 60L);

        List<ExtractionTask> stuckTasks = taskRepository.findStuckTasks(threshold);

        if (stuckTasks.isEmpty()) {
            return;
        }

        log.warn("⚠️  Found {} stuck tasks, marking for retry", stuckTasks.size());

        for (ExtractionTask task : stuckTasks) {
            try {
                log.warn("Recovering stuck task: {} (started at: {})",
                        task.getId(), task.getStartedAt());

                int retryDelay = calculateRetryDelay(task.getAttempts());
                task.markForRetry("Task stuck for more than " + thresholdMinutes + " minutes", retryDelay);
                taskRepository.save(task);
            } catch (Exception e) {
                log.error("Failed to recover stuck task {}", task.getId(), e);
            }
        }
    }

    /**
     * Implementación simple de MultipartFile para uso interno
     * (Copiada de ExtractionController)
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
