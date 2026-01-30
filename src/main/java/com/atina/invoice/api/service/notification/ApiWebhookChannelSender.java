package com.atina.invoice.api.service.notification;

import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.model.enums.NotificationChannel;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiWebhookChannelSender implements NotificationChannelSender {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.API_WEBHOOK;
    }

    @Override
    public void send(NotificationContext context, TenantNotificationRule rule) {
        try {
            String url = null;
            Map<String, String> headers = new HashMap<>();

            if (rule.getChannelConfig() != null && !rule.getChannelConfig().isBlank()) {
                JsonNode config = objectMapper.readTree(rule.getChannelConfig());
                if (config.has("url")) {
                    url = config.get("url").asText();
                }
                if (config.has("headers")) {
                    JsonNode headersNode = config.get("headers");
                    headersNode.fieldNames().forEachRemaining(field ->
                            headers.put(field, headersNode.get(field).asText()));
                }
            }

            if (url == null || url.isBlank()) {
                log.warn("No API webhook URL configured for rule {}, skipping", rule.getId());
                return;
            }

            Map<String, Object> payload = buildPayload(context, rule);
            String payloadJson = objectMapper.writeValueAsString(payload);

            RestClient.RequestBodySpec request = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request = request.header(entry.getKey(), entry.getValue());
            }

            request.body(payloadJson)
                    .retrieve()
                    .toBodilessEntity();

            log.info("API webhook notification sent to {} for event {} (rule {})",
                    url, rule.getEvent(), rule.getId());

        } catch (Exception e) {
            log.error("Failed to send API webhook notification for rule {}: {}",
                    rule.getId(), e.getMessage(), e);
        }
    }

    private Map<String, Object> buildPayload(NotificationContext context, TenantNotificationRule rule) {
        Map<String, Object> payload = new HashMap<>();
        ProcessedEmail email = context.getEmail();

        payload.put("event", rule.getEvent().name());
        payload.put("timestamp", Instant.now().toString());
        payload.put("correlation_id", email.getCorrelationId());
        payload.put("sender_email", email.getFromAddress());
        payload.put("subject", email.getSubject());
        payload.put("tenant_code", context.getTenant().getTenantCode());

        switch (rule.getEvent()) {
            case EMAIL_RECEIVED:
                payload.put("total_attachments", email.getTotalAttachments());
                payload.put("processed_attachments", email.getProcessedAttachments());
                if (context.getAttachments() != null) {
                    payload.put("attachments", context.getAttachments().stream()
                            .map(att -> {
                                Map<String, Object> m = new HashMap<>();
                                m.put("filename", att.getOriginalFilename());
                                m.put("status", att.getProcessingStatus().name());
                                return m;
                            })
                            .collect(Collectors.toList()));
                }
                break;

            case EXTRACTION_COMPLETED:
                if (context.getTasks() != null) {
                    long completed = context.getTasks().stream()
                            .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED).count();
                    long failed = context.getTasks().stream()
                            .filter(t -> t.getStatus() == ExtractionStatus.FAILED).count();

                    payload.put("total_files", context.getTasks().size());
                    payload.put("extracted_files", completed);
                    payload.put("failed_files", failed);
                    payload.put("success_rate", context.getTasks().size() > 0
                            ? (completed * 100.0 / context.getTasks().size()) : 0.0);

                    payload.put("extractions", context.getTasks().stream()
                            .map(this::taskToPayloadMap)
                            .collect(Collectors.toList()));
                }
                break;

            case WEBHOOK_CALLBACK:
                if (context.getCallbackResponse() != null) {
                    WebhookCallbackResponse cb = context.getCallbackResponse();
                    payload.put("callback_status", cb.getStatus());
                    payload.put("callback_reference", cb.getReference());
                    payload.put("callback_message", cb.getMessage());
                }
                break;
        }

        return payload;
    }

    private Map<String, Object> taskToPayloadMap(ExtractionTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put("task_id", task.getId());
        map.put("correlation_id", task.getCorrelationId());
        map.put("original_filename", task.getAttachment().getOriginalFilename());
        map.put("source", task.getSource());
        map.put("status", task.getStatus().name());

        if (task.getStatus() == ExtractionStatus.COMPLETED && task.getRawResult() != null) {
            try {
                JsonNode result = objectMapper.readTree(task.getRawResult());
                map.put("extracted_data", result);
            } catch (Exception e) {
                log.warn("Failed to parse result for task {}", task.getId());
            }
        }

        if (task.getStatus() == ExtractionStatus.FAILED) {
            map.put("error_message", task.getErrorMessage());
        }

        return map;
    }
}
