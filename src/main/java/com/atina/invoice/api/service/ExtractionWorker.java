package com.atina.invoice.api.service;

import com.atina.invoice.api.config.ExtractionProperties;
import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.repository.ExtractionTaskRepository;
import com.atina.invoice.api.repository.ExtractionTemplateRepository;
import com.atina.invoice.api.repository.ProcessedEmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    private final ProcessedEmailRepository emailRepository;
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

        // ---------------------------------------------------------------------------------------
        // Buscar tareas PENDING o RETRYING que deban ejecutarse
        // ---------------------------------------------------------------------------------------

        List<ExtractionTask> tasks = taskRepository.findNextTasksToProcess(Instant.now());

        if (tasks.isEmpty()) {
            return;
        }

        log.info("🔄 Found {} tasks to process", tasks.size());

        int batchSize = properties.getWorker().getBatchSize();
        int processed = 0;

        for (ExtractionTask task : tasks) {

            if (processed >= batchSize) {

                log.info("Batch size limit reached ({}), stopping", batchSize);

                break;
            }

            try {

                // ---------------------------
                // Procesar tarea individual
                // ---------------------------

                processTask(task);

                // ------------------------------
                // Contador de tareas procesadas
                // ------------------------------

                processed++;

            } catch (Exception e) {
                log.error("Error processing task {}: {}", task.getId(), e.getMessage(), e);
                handleError(task, e);
            }
        }

        // ------------------------------
        // Resumen de tareas procesadas
        // ------------------------------

        if (processed > 0) {
            log.info("✅ Processed {} tasks", processed);
        }
    }

    /**
     * Procesar una tarea de extracción
     */
    @Transactional
    protected void processTask(ExtractionTask task) {
        Long taskId = task.getId();

        log.info("🔄 [TASK-{}] Processing extraction task for PDF: {}",
                taskId, task.getPdfPath());

        // ---------------------------------------------------------------------------------------
        // 1. Re-cargar la tarea con todas sus relaciones para evitar LazyInitializationException
        // ---------------------------------------------------------------------------------------

        task = taskRepository.findByIdWithRelations(taskId);
        if (task == null) {
            log.error("❌ [TASK-{}] Task not found when reloading", taskId);
            return;
        }

        // ---------------------------------------------------------------------------------------
        // 2. Extraer datos necesarios ANTES de hacer save() (para evitar lazy loading después)
        // ---------------------------------------------------------------------------------------

        ProcessedEmail email = task.getEmail();
        Tenant tenant = email.getTenant();
        ProcessedAttachment attachment = task.getAttachment();
        Long tenantId = tenant.getId();
        String source = task.getSource();
        String pdfPath = task.getPdfPath();

        log.info("Starting extraction for task:");

        log.info("    Tenant Code: {}",tenant.getTenantCode());
        log.info("    From EMail: {}", email.getFromAddress());
        log.info("    Attacment File: {}", attachment.getNormalizedFilename());

        // -----------------------
        // 3. Mark as processing
        // -----------------------

        task.markAsProcessing();

        taskRepository.save(task);

        log.info("Marked task as PROCESSING");

        try {

            // ----------------------------------------
            // 4. Buscar template para (tenant, source)
            // ----------------------------------------

            log.info("[TASK-{}] Looking for template: tenant={}, source={}",
                    taskId, tenantId, source);

            ExtractionTemplate templateConfig = templateRepository
                    .findByTenantIdAndSourceAndIsActive(tenantId, source, true)
                    .orElseThrow(() -> new RuntimeException(
                            String.format("No active template found for tenant=%d, source=%s",
                                    tenantId, source)
                    ));

            log.info("[TASK-{}] Found template: {} (name: {}, fullPath: {})",
                    taskId, templateConfig.getDescription(),
                    templateConfig.getTemplateName(), templateConfig.getFullTemplatePath());

            // ----------------------------------------
            // 5. Cargar PDF como MultipartFile
            // ----------------------------------------

            File pdfFile = new File(pdfPath);

            if (!pdfFile.exists()) {
                throw new FileNotFoundException("PDF file not found: " + pdfPath);
            }

            log.info("[TASK-{}] Loading PDF file: {} ({} bytes)",
                    taskId, pdfFile.getName(), pdfFile.length());

            MultipartFile multipartFile = convertFileToMultipartFile(pdfFile);

            // ---------------------------------------------
            // 6. PDF → JSON interno (usando DoclingService)
            // ---------------------------------------------

            log.info("[TASK-{}] Converting PDF to internal format...", taskId);

            JsonNode processedData = doclingService.convertPdf(multipartFile);

            // ------------------------------------------------
            // 7. Cargar template desde filesystem
            // ------------------------------------------------

            String fullTemplatePath = templateConfig.getFullTemplatePath();
            log.info("[TASK-{}] Loading template from: {}",
                    taskId, fullTemplatePath);

            File templateFile = new File(fullTemplatePath);
            if (!templateFile.exists()) {
                throw new FileNotFoundException(
                        "Template file not found: " + fullTemplatePath
                );
            }

            JsonNode template = objectMapper.readTree(templateFile);

            // ------------------------------------------------
            // 8. Extraer datos (usando ExtractionService)
            // ------------------------------------------------

            log.info("[TASK-{}] Extracting data from PDF...", taskId);

            ExtractionOptions options = new ExtractionOptions();
            options.setIncludeMeta(true);
            options.setIncludeEvidence(false);
            options.setFailOnValidation(false);

            JsonNode result = extractionService.extract(processedData, template, options);

            // ------------------------------------------------
            // 9. Guardar resultado JSON
            // ------------------------------------------------

            String resultJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);
            String resultPath = saveExtractionResult(tenant, email, attachment, resultJson);

            log.info("[TASK-{}] Extraction result saved to: {}", taskId, resultPath);

            // ------------------------------------------------
            // 10. Actualizar tarea como completada
            // ------------------------------------------------

            task.markAsCompleted(resultPath, resultJson);
            taskRepository.save(task);

            log.info("✅ [TASK-{}] Extraction completed successfully", taskId);

            // ---------------------------------------------------
            // 11. Verificar si email está completamente procesado
            // ---------------------------------------------------

            checkEmailCompletion(email);

        } catch (Exception e) {

            log.error("❌ [TASK-{}] Extraction failed: {}", taskId, e.getMessage(), e);

            int retryDelay = calculateRetryDelay(task.getAttempts());
            task.markForRetry(e.getMessage(), retryDelay);
            taskRepository.save(task);

            log.warn("[TASK-{}] Marked for retry (attempts: {}/{}, next retry in {}s)",
                    task.getId(), task.getAttempts(), task.getMaxAttempts(), retryDelay);

            // -----------------------------------------------------------
            // Si ya no se reintentará más, verificar completion del email
            // -----------------------------------------------------------

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
    private String saveExtractionResult(Tenant tenant, ProcessedEmail email, ProcessedAttachment attachment, String resultJson) throws IOException {

        Objects.requireNonNull(tenant, "tenant is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(attachment, "attachment is required");
        Objects.requireNonNull(resultJson, "resultJson is required");

        String basePath = tenant.getStorageBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException("Tenant storageBasePath is missing");
        }
        String tenantCode = tenant.getTenantCode();
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalStateException("Tenant tenantCode is missing");
        }

        // /{basePath}/{tenantCode}/process/extractions/
        Path directory = Paths.get(basePath, tenantCode, "process", "extractions");
        Files.createDirectories(directory);

        // Safer filename + deterministic uniqueness
        String normalized = attachment.getNormalizedFilename();
        if (normalized == null || normalized.isBlank()) {
            normalized = "attachment";
        }
        normalized = normalized.replaceAll("[\\\\/\\r\\n\\t]", "_"); // avoid path traversal / weird chars

        // Example: {emailId}_{attachmentId}_{normalized}.extraction.json
        String filename = String.format("%s.extraction.json",
                normalized
        );

        Path filePath = directory.resolve(filename).normalize();
        if (!filePath.startsWith(directory)) {
            throw new IllegalStateException("Invalid filename produced a path outside the target directory");
        }

        Files.writeString(
                filePath,
                resultJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        log.info("Saved extraction result: tenant={} emailId={} attachmentId={} path={}",
                tenantCode, email.getId(), attachment.getId(), filePath);

        return filePath.toString();
    }

    /**
     * Verificar si email está completamente procesado
     * Si sí, enviar webhook y notificación email
     */
    @Transactional
    protected void checkEmailCompletion(ProcessedEmail email) {
        Long emailId = email.getId();

        // Re-cargar email con relaciones (tenant, senderRule) para evitar LazyInitializationException
        email = emailRepository.findByIdWithRelations(emailId)
                .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));

        List<ExtractionTask> tasks = taskRepository
                .findByEmailIdOrderByCreatedAtAsc(emailId);

        boolean allDone = tasks.stream().allMatch(ExtractionTask::isTerminal);

        if (!allDone) {
            log.info("[EMAIL-{}] Not all tasks completed yet", emailId);
            return;
        }

        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();

        log.info("✅ [EMAIL-{}] All extraction tasks completed: {}/{} successful, {} failed",
                emailId, completed, tasks.size(), failed);

        // ------------------------------------------------
        // Enviar webhook si está configurado
        // ------------------------------------------------

        if (properties.getWebhook().isEnabled() &&
            email.getTenant() != null &&
            email.getTenant().getWebhookUrl() != null &&
            !email.getTenant().getWebhookUrl().isBlank()) {

            try {

                log.info("[EMAIL-{}] Sending webhook notification", emailId);
                webhookService.sendExtractionCompletedWebhook(email, tasks);

            } catch (Exception e) {

                log.error("[EMAIL-{}] Failed to send webhook: {}",
                        emailId, e.getMessage(), e);

            }
        }

        // Enviar email de procesamiento completado si está configurado
        if (email.getSenderRule() != null &&
            email.getSenderRule().getProcessEnabled() &&
            email.getSenderRule().getTemplateEmailProcessed() != null) {

            try {
                log.info("[EMAIL-{}] Sending processed email notification", emailId);
                emailNotificationService.sendProcessedEmail(email, tasks);
            } catch (Exception e) {
                log.error("[EMAIL-{}] Failed to send email notification: {}",
                        emailId, e.getMessage(), e);
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

            // Re-cargar tarea para evitar problemas de concurrencia

            task = taskRepository.findById(task.getId()).orElse(task);

            // Solo marcar para retry si está en PROCESSING

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

        // Verificar si el worker está habilitado

        if (!properties.getWorker().isEnabled()) {
            return;
        }

        // Buscar tareas atascadas

        int thresholdMinutes = properties.getWorker().getStuckTaskThresholdMinutes();
        Instant threshold = Instant.now().minusSeconds(thresholdMinutes * 60L);

        List<ExtractionTask> stuckTasks = taskRepository.findStuckTasks(threshold);

        if (stuckTasks.isEmpty()) {
            return;
        }

        log.warn("⚠️  Found {} stuck tasks, marking for retry", stuckTasks.size());

        // Marcar cada tarea atascada para retry

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
