package com.atina.invoice.api.service;

import com.atina.invoice.api.config.ExtractionProperties;
import com.atina.invoice.api.dto.request.ExtractionOptions;
import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.model.enums.WebhookEventStatus;
import com.atina.invoice.api.repository.ExtractionTaskRepository;
import com.atina.invoice.api.repository.ExtractionTemplateRepository;
import com.atina.invoice.api.repository.ProcessedEmailRepository;
import com.atina.invoice.api.repository.WebhookEventRepository;
import com.atina.invoice.api.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
import java.util.*;
import java.util.stream.Collectors;

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
    private final WebhookEventRepository webhookEventRepository;
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
        // 2. Generar o reutilizar correlationId para tracking
        // ---------------------------------------------------------------------------------------

        String correlationId = task.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            task.setCorrelationId(correlationId);
            log.info("Generated new correlationId for task {}: {}", taskId, correlationId);
        } else {
            log.info("Reusing existing correlationId for task {}: {}", taskId, correlationId);
        }

        // Poner correlationId en MDC para que aparezca en todos los logs
        MDC.put("correlationId", correlationId);

        try {
            // ---------------------------------------------------------------------------------------
            // 3. Extraer datos necesarios ANTES de hacer save() (para evitar lazy loading después)
            // ---------------------------------------------------------------------------------------

            ProcessedEmail email = task.getEmail();
            Tenant tenant = email.getTenant();
            ProcessedAttachment attachment = task.getAttachment();
            Long tenantId = tenant.getId();
            String tenantCode = tenant.getTenantCode();
            String source = task.getSource();
            String pdfPath = task.getPdfPath();

            // ---------------------------------------------------------------------------------------
            // Set TenantContext for background thread
            // This allows nested service calls (metrics, webhooks, etc.) to access tenant info
            // ---------------------------------------------------------------------------------------
            TenantContext.setTenantId(tenantId);
            TenantContext.setTenantCode(tenantCode);
            log.debug("[TASK-{}] Set TenantContext: tenantId={}, tenantCode={}", taskId, tenantId, tenantCode);

            log.info("Starting extraction for task:");

            log.info("    Tenant Code: {}", tenantCode);
            log.info("    From EMail: {}", email.getFromAddress());
            log.info("    Attacment File: {}", attachment.getNormalizedFilename());

            // -----------------------
            // 4. Mark as processing
            // -----------------------

            task.markAsProcessing();

            taskRepository.save(task);

            log.info("Marked task as PROCESSING");

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
            // 9b. Verificar validaciones de extracción
            // ------------------------------------------------
            JsonNode validations = result.get("validations");

            if (validations != null && validations.isArray() && validations.size() > 0) {

                StringBuilder validationErrors = new StringBuilder("Extraction validation failed: ");

                for (JsonNode v : validations) {
                    String path = v.has("path") ? v.get("path").asText() : "";
                    String type = v.has("type") ? v.get("type").asText() : "";
                    String message = v.has("message") ? v.get("message").asText() : "";
                    validationErrors.append(String.format("[%s/%s: %s] ", path, type, message));
                }

                log.warn("[TASK-{}] Extraction has {} validation error(s): {}",
                        taskId, validations.size(), validationErrors);

                task.markAsFailed(validationErrors.toString().trim());
                task.setResultPath(resultPath);
                task.setRawResult(resultJson);

                taskRepository.save(task);

                // ------------------------------------------------
                // Verificar si email está completamente procesado
                // ------------------------------------------------

                checkEmailCompletion(email);

                return;
            }

            // ------------------------------------------------
            // 10. Actualizar tarea como completada
            // ------------------------------------------------

            task.markAsCompleted(resultPath, resultJson);

            taskRepository.save(task);

            log.info("✅ [TASK-{}] Extraction completed successfully", taskId);

            // ------------------------------------------------
            // CREAR WEBHOOK EVENT POR PDF (Transactional Outbox)
            // ------------------------------------------------

            if (properties.getWebhook().isEnabled() &&
                tenant.getWebhookUrl() != null &&
                !tenant.getWebhookUrl().isBlank()) {

                try {
                    log.info("[TASK-{}] Creating webhook event for completed task", taskId);

                    // Build task webhook payload
                    Map<String, Object> payload = buildTaskWebhookPayload(task, email, tenant);
                    String payloadJson = objectMapper.writeValueAsString(payload);

                    // Create webhook event in SAME TRANSACTION as task save
                    WebhookEvent event = WebhookEvent.builder()
                            .tenantId(tenant.getId())
                            .eventType("extraction_task_completed")
                            .entityType("ExtractionTask")
                            .entityId(task.getId())
                            .payload(payloadJson)
                            .status(WebhookEventStatus.PENDING)
                            .attempts(0)
                            .maxAttempts(properties.getWebhook().getRetryAttempts())
                            .build();

                    webhookEventRepository.save(event);

                    log.info("✅ [TASK-{}] Webhook event created: {}", taskId, event.getId());

                } catch (Exception e) {
                    log.error("❌ [TASK-{}] Failed to create webhook event: {}",
                              taskId, e.getMessage(), e);
                }
            }

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

        } finally {
            // Limpiar TenantContext y correlationId del MDC
            TenantContext.clear();
            MDC.remove("correlationId");
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

        // --------------------------------------------------
        // Re-cargar email con relaciones (tenant, senderRule)
        // para evitar LazyInitializationException
        // --------------------------------------------------

        Long emailId = email.getId();

        email = emailRepository.findByIdWithRelations(emailId)
                .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));

        // ------------------------------------------------------
        // Verificar si todas las tareas están en estado terminal
        // ------------------------------------------------------

        List<ExtractionTask> tasks = taskRepository
                .findByEmailIdOrderByCreatedAtAsc(emailId);

        boolean allDone = tasks.stream().allMatch(ExtractionTask::isTerminal);

        // ------------------------------------------------------
        // Si no todas las tareas están completas, salir
        // ------------------------------------------------------

        if (!allDone) {

            log.info("[EMAIL-{}] Not all tasks completed yet", emailId);

            return;

        }

        // ------------------------------------------------------
        // Resumen de resultados
        // ------------------------------------------------------

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

                log.info("[EMAIL-{}] Creating CONSOLIDATED webhook event (Transactional Outbox)", emailId);

                // ----------------------------------------
                // Build email-level payload (summary)
                // ----------------------------------------

                Map<String, Object> payload = buildEmailWebhookPayload(email, tasks);
                String payloadJson = objectMapper.writeValueAsString(payload);

                // ------------------------------------------
                // Create webhook event for email completion
                // ------------------------------------------

                WebhookEvent event = WebhookEvent.builder()
                        .tenantId(email.getTenant().getId())
                        .eventType("extraction_email_completed")  // Diferente de "extraction_task_completed"
                        .entityType("ProcessedEmail")
                        .entityId(email.getId())
                        .payload(payloadJson)
                        .status(WebhookEventStatus.PENDING)
                        .attempts(0)
                        .maxAttempts(properties.getWebhook().getRetryAttempts())
                        .build();

                webhookEventRepository.save(event);

                log.info("✅ [EMAIL-{}] Webhook event created: {}", emailId, event.getId());

            } catch (Exception e) {
                log.error("❌ [EMAIL-{}] Failed to create webhook event: {}",
                        emailId, e.getMessage(), e);
            }
        }

        // --------------------------------------------------------------
        // Enviar email de procesamiento completado si está configurado
        // --------------------------------------------------------------

        if (email.getSenderRule() != null &&
            email.getSenderRule().getProcessEnabled() &&
            email.getSenderRule().getTemplateEmailProcessed() != null) {

            try {

                // ------------------------------------------------
                // Enviar notificación email
                // ------------------------------------------------

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

    // ========================================
    // Webhook Payload Building Methods
    // ========================================

    /**
     * Construir payload del webhook para UN task individual completado
     */
    private Map<String, Object> buildTaskWebhookPayload(
            ExtractionTask task,
            ProcessedEmail email,
            Tenant tenant) {

        Map<String, Object> payload = new HashMap<>();

        // Event info
        payload.put("event_type", "extraction_task_completed");
        payload.put("timestamp", Instant.now().toString());

        // Email context
        payload.put("email_id", email.getId());
        payload.put("email_correlation_id", email.getCorrelationId());
        payload.put("sender_email", email.getFromAddress());
        payload.put("subject", email.getSubject());

        // Task info
        payload.put("task_id", task.getId());
        payload.put("task_correlation_id", task.getCorrelationId());
        payload.put("original_filename", task.getAttachment().getOriginalFilename());
        payload.put("normalized_filename", task.getAttachment().getNormalizedFilename());
        payload.put("source", task.getSource());
        payload.put("status", task.getStatus().name());

        // Extraction result
        if (task.getStatus() == ExtractionStatus.COMPLETED) {
            if (task.getRawResult() != null) {
                try {
                    JsonNode result = objectMapper.readTree(task.getRawResult());
                    payload.put("extracted_data", result);
                } catch (Exception e) {
                    log.warn("Failed to parse result for task {}", task.getId());
                    payload.put("extracted_data", null);
                    payload.put("parse_error", e.getMessage());
                }
            }
        }

        if (task.getStatus() == ExtractionStatus.FAILED) {
            payload.put("error_message", task.getErrorMessage());
        }

        return payload;
    }

    /**
     * Construir payload completo del webhook para un email procesado (consolidado)
     */
    private Map<String, Object> buildEmailWebhookPayload(ProcessedEmail email, List<ExtractionTask> tasks) {
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();

        Map<String, Object> payload = new HashMap<>();

        // Event info
        payload.put("event_type", "extraction_email_completed");
        payload.put("timestamp", Instant.now().toString());

        // Email info
        payload.put("email_id", email.getId());
        payload.put("correlation_id", email.getCorrelationId());
        payload.put("sender_email", email.getFromAddress());
        payload.put("subject", email.getSubject());
        payload.put("received_date", email.getReceivedDate() != null
                ? email.getReceivedDate().toString() : null);

        // Extraction stats
        payload.put("total_files", tasks.size());
        payload.put("extracted_files", completed);
        payload.put("failed_files", failed);
        payload.put("success_rate", tasks.size() > 0
                ? (completed * 100.0 / tasks.size()) : 0.0);

        // Individual extractions
        payload.put("extractions", tasks.stream()
                .map(this::taskToPayloadMap)
                .collect(Collectors.toList()));

        return payload;
    }

    /**
     * Convertir ExtractionTask a Map para el payload del webhook
     */
    private Map<String, Object> taskToPayloadMap(ExtractionTask task) {
        Map<String, Object> map = new HashMap<>();

        map.put("task_id", task.getId());
        map.put("correlation_id", task.getCorrelationId());
        map.put("original_filename", task.getAttachment().getOriginalFilename());
        map.put("normalized_filename", task.getAttachment().getNormalizedFilename());
        map.put("source", task.getSource());
        map.put("status", task.getStatus().name());

        if (task.getStatus() == ExtractionStatus.COMPLETED) {
            if (task.getRawResult() != null) {
                try {
                    JsonNode result = objectMapper.readTree(task.getRawResult());
                    map.put("extracted_data", result);
                } catch (Exception e) {
                    log.warn("Failed to parse result for task {}", task.getId());
                }
            }
        }

        if (task.getStatus() == ExtractionStatus.FAILED) {
            map.put("error_message", task.getErrorMessage());
        }

        return map;
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
