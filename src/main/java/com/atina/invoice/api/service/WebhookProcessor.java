package com.atina.invoice.api.service;

import com.atina.invoice.api.config.ExtractionProperties;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.WebhookEvent;
import com.atina.invoice.api.model.enums.WebhookEventStatus;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.repository.WebhookEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Procesador de eventos de webhook usando el patrón Transactional Outbox
 *
 * Este servicio:
 * 1. Cada X segundos busca eventos de webhook PENDING listos para enviar
 * 2. Para cada evento:
 *    - Marca como SENDING
 *    - Envía el webhook HTTP
 *    - Si éxito: marca como SENT
 *    - Si falla: marca como PENDING para retry (con exponential backoff) o FAILED si se agotaron intentos
 *
 * Ventajas del patrón Outbox:
 * - Garantía de entrega (at-least-once)
 * - Visibilidad completa de todos los webhooks
 * - Reintentos automáticos
 * - Posibilidad de reintento manual
 * - Auditoría completa
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final TenantRepository tenantRepository;
    private final WebhookService webhookService;
    private final ExtractionProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Procesar eventos de webhook pendientes
     * Se ejecuta cada 5 segundos por defecto
     */
    @Scheduled(fixedDelayString = "${extraction.webhook.processor-interval-ms:5000}")
    public void processPendingWebhooks() {
        if (!properties.getWebhook().isEnabled()) {
            return;
        }

        int batchSize = 10;
        List<WebhookEvent> pending = webhookEventRepository.findPendingEvents(
                Instant.now(),
                PageRequest.of(0, batchSize)
        );

        if (pending.isEmpty()) {
            return;
        }

        log.info("Processing {} pending webhook events", pending.size());

        for (WebhookEvent event : pending) {
            processWebhookEvent(event);
        }
    }

    /**
     * Procesar un evento de webhook individual
     */
    @Transactional
    protected void processWebhookEvent(WebhookEvent event) {
        Long eventId = event.getId();

        try {
            log.info("[WEBHOOK-{}] Processing event (attempt {}/{})",
                    eventId, event.getAttempts() + 1, event.getMaxAttempts());

            // Marcar como enviando
            event.markAsSending();
            webhookEventRepository.save(event);

            // Obtener tenant
            Tenant tenant = tenantRepository.findById(event.getTenantId())
                    .orElseThrow(() -> new RuntimeException("Tenant not found: " + event.getTenantId()));

            String webhookUrl = tenant.getWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank()) {
                throw new RuntimeException("Tenant has no webhook URL configured");
            }

            // Parsear payload
            Map<String, Object> payload = objectMapper.readValue(
                    event.getPayload(),
                    new TypeReference<Map<String, Object>>() {}
            );

            // Enviar webhook
            webhookService.sendWebhookDirect(webhookUrl, payload);

            // Marcar como enviado
            event.markAsSent();
            webhookEventRepository.save(event);

            log.info("✅ [WEBHOOK-{}] Sent successfully", eventId);

        } catch (Exception e) {
            log.error("❌ [WEBHOOK-{}] Failed: {}", eventId, e.getMessage(), e);

            // Marcar como fallido (auto-retry si attempts < max)
            event.markAsFailed(e.getMessage());
            webhookEventRepository.save(event);

            if (event.getStatus() == WebhookEventStatus.FAILED) {
                log.error("[WEBHOOK-{}] Max attempts exceeded, marked as FAILED", eventId);
            } else {
                log.info("[WEBHOOK-{}] Scheduled for retry at: {}",
                        eventId, event.getNextRetryAt());
            }
        }
    }

    /**
     * Reintentar webhooks fallidos (trigger manual via API)
     */
    @Transactional
    public int retryFailedWebhooks() {
        List<WebhookEvent> failed = webhookEventRepository.findByStatus(WebhookEventStatus.FAILED);

        log.info("Retrying {} failed webhook events", failed.size());

        for (WebhookEvent event : failed) {
            event.resetForRetry();
            webhookEventRepository.save(event);
        }

        return failed.size();
    }

    /**
     * Reintentar un webhook específico
     */
    @Transactional
    public void retryWebhookEvent(Long eventId) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Webhook event not found: " + eventId));

        log.info("Manually retrying webhook event: {}", eventId);

        event.resetForRetry();
        webhookEventRepository.save(event);
    }
}
