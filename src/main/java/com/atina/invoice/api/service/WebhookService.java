package com.atina.invoice.api.service;

import com.atina.invoice.api.config.ExtractionProperties;
import com.atina.invoice.api.model.ExtractionTask;
import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para enviar webhooks de notificación
 *
 * Envía notificaciones HTTP POST cuando se completan extracciones de PDFs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExtractionProperties properties;

    /**
     * Enviar webhook cuando se completan todas las extracciones de un email
     *
     * @param email Email procesado
     * @param tasks Tareas de extracción del email
     */
    @Async
    public void sendExtractionCompletedWebhook(ProcessedEmail email, List<ExtractionTask> tasks) {

        // ---------------------------------------
        // Obtener URL del webhook del tenant
        // ---------------------------------------

        String webhookUrl = email.getTenant().getWebhookUrl();

        // ---------------------------------------
        // Si no hay URL configurada, salir
        // ---------------------------------------

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("No webhook URL configured for tenant: {}", email.getTenant().getTenantCode());
            return;
        }

        log.info("📤 Sending extraction webhook for email {} to: {}", email.getId(), webhookUrl);

        Map<String, Object> payload = buildWebhookPayload(email, tasks);

        // -------------------------------
        // Enviar con retry logic
        // -------------------------------

        int maxAttempts = properties.getWebhook().getRetryAttempts();
        sendWithRetry(webhookUrl, payload, maxAttempts);
    }

    /**
     * Construir payload del webhook
     */
    private Map<String, Object> buildWebhookPayload(ProcessedEmail email, List<ExtractionTask> tasks) {


        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();
        long cancelled = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.CANCELLED)
                .count();

        int total = tasks != null ? tasks.size() : 0;
        double successRate = total > 0 ? (completed * 100.0 / total) : 0.0;

        Map<String, Object> payload = new HashMap<>();

        // Event info
        payload.put("event_type", "extraction_email_completed");
        payload.put("timestamp", Instant.now().toString());

        // Email info
        payload.put("correlation_id", email.getCorrelationId());
        payload.put("sender_email", email.getFromAddress());
        payload.put("subject", email.getSubject());
        payload.put("received_date", email.getReceivedDate() != null
                ? email.getReceivedDate().toString()
                : null);

        // Extraction stats
        payload.put("total_files", tasks.size());
        payload.put("extracted_files", completed);
        payload.put("failed_files", failed);
        payload.put("cancelled_files", cancelled);
        payload.put("success_rate", tasks.size() > 0
                ? (completed * 100.0 / tasks.size())
                : 0.0);

        // -----------------------------
        // Individual extractions
        // -----------------------------
        payload.put("extractions", tasks.stream()
                .map(this::taskToPayload)
                .collect(Collectors.toList()));


        log.info(
                "Webhook payload built: tenant={} emailId={} correlationId={} totalTasks={} completed={} failed={} cancelled={} successRate={}%",
                email.getTenant() != null ? email.getTenant().getTenantCode() : null,
                email.getId(),
                email.getCorrelationId(),
                total,
                completed,
                failed,
                cancelled,
                String.format(java.util.Locale.US, "%.2f", successRate));

        return payload;
    }

    /**
     * Convertir task a payload
     */
    private Map<String, Object> taskToPayload(ExtractionTask task) {

        // -----------------------------------------
        // Campos básicos
        // -----------------------------------------

        log.info("taskToPayload start: taskId={}, status={}, attempts={}, source={}, originalFilename={}, normalizedFilename={}",
                task.getId(),
                task.getStatus(),
                task.getAttempts(),
                task.getSource(),
                task.getAttachment() != null ? task.getAttachment().getOriginalFilename() : null,
                task.getAttachment() != null ? task.getAttachment().getNormalizedFilename() : null
        );


        Map<String, Object> map = new HashMap<>();

        map.put("task_id", task.getId());
        map.put("original_filename", task.getAttachment().getOriginalFilename());
        map.put("normalized_filename", task.getAttachment().getNormalizedFilename());
        map.put("source", task.getSource());
        map.put("status", task.getStatus().name());
        map.put("attempts", task.getAttempts());

        // Timestamps
        map.put("created_at", task.getCreatedAt() != null
                ? task.getCreatedAt().toString()
                : null);
        map.put("completed_at", task.getCompletedAt() != null
                ? task.getCompletedAt().toString()
                : null);

        // -----------------------------------------
        // Si completó, incluir datos extraídos
        // -----------------------------------------

        if (task.getStatus() == ExtractionStatus.COMPLETED && task.getRawResult() != null) {

            log.info("taskToPayload: task {} COMPLETED; parsing rawResult. resultPath={}", task.getId(), task.getResultPath());

            try {
                Map<String, Object> extractedData = objectMapper.readValue(
                        task.getRawResult(),
                        new TypeReference<>() {}
                );
                map.put("extracted_data", extractedData);
                map.put("result_path", task.getResultPath());

                log.info("taskToPayload: task {} rawResult parsed OK. extractedKeys={}, extractedSize={}",
                        task.getId(),
                        extractedData != null ? extractedData.keySet() : null,
                        extractedData != null ? extractedData.size() : 0
                );

            } catch (Exception e) {
                log.warn("Failed to parse extraction result for task {}", task.getId(), e);
                map.put("extracted_data", null);
                map.put("result_parse_error", e.getMessage());
                log.info("taskToPayload: task {} rawResult parsed FAILED. error={}", task.getId(), e.getMessage());
            }
        } else if (task.getStatus() == ExtractionStatus.COMPLETED) {
            log.info("taskToPayload: task {} COMPLETED but rawResult is null. resultPath={}", task.getId(), task.getResultPath());
        }

        // -----------------------------------------
        // Si falló, incluir error
        // -----------------------------------------

        if (task.getStatus() == ExtractionStatus.FAILED) {
            log.info("taskToPayload: task {} FAILED. errorMessage={}", task.getId(), task.getErrorMessage());
            map.put("error_message", task.getErrorMessage());
        }

        log.info("taskToPayload end: taskId={}, payloadKeys={}", task.getId(), map.keySet());

        return map;
    }

    /**
     * Enviar webhook con retry logic
     */
    private void sendWithRetry(String url, Map<String, Object> payload, int maxAttempts) {
        int baseDelay = properties.getWebhook().getRetryDelaySeconds();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sendWebhookRequest(url, payload);
                log.info("✅ Webhook sent successfully to: {} (attempt {})", url, attempt);
                return; // Success, exit

            } catch (Exception e) {
                log.error("❌ Webhook failed (attempt {}/{}): {}",
                        attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    // Calculate exponential backoff delay
                    int delay = baseDelay * (int) Math.pow(2, attempt - 1);
                    log.info("Retrying webhook in {} seconds...", delay);

                    try {
                        Thread.sleep(delay * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Webhook retry interrupted");
                        return;
                    }
                } else {
                    log.error("❌ Webhook failed after {} attempts, giving up", maxAttempts);
                }
            }
        }
    }

    /**
     * Enviar request HTTP POST del webhook
     */
    private void sendWebhookRequest(String url, Map<String, Object> payload) {
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Atina-Invoice-Extractor/1.0")
                    .header("X-Webhook-Event", (String) payload.get("event_type"))
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.debug("Webhook response: {}", response);

        } catch (Exception e) {
            throw new RuntimeException("Webhook request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Enviar webhook directamente sin retry interno
     * Usado por WebhookProcessor que maneja los reintentos via base de datos
     *
     * @param url URL del webhook
     * @param payload Payload ya construido
     * @throws RuntimeException si el envío falla
     */
    public void sendWebhookDirect(String url, Map<String, Object> payload) {
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("User-Agent", "Atina-Invoice-Extractor/1.0")
                    .header("X-Webhook-Event", (String) payload.get("event_type"))
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("Webhook sent successfully to {}", url);

        } catch (Exception e) {
            log.error("Webhook request failed: {}", e.getMessage());
            throw new RuntimeException("Webhook sending failed: " + e.getMessage(), e);
        }
    }
}
