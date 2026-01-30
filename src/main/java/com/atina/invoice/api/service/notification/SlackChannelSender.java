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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackChannelSender implements NotificationChannelSender {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SLACK;
    }

    @Override
    public void send(NotificationContext context, TenantNotificationRule rule) {
        try {
            String webhookUrl = resolveWebhookUrl(rule);
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.warn("No Slack webhook_url configured for rule {}, skipping", rule.getId());
                return;
            }

            String message = buildSlackMessage(context, rule);

            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);

            String payloadJson = objectMapper.writeValueAsString(payload);

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadJson)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Slack notification sent for event {} (rule {})", rule.getEvent(), rule.getId());

        } catch (Exception e) {
            log.error("Failed to send Slack notification for rule {}: {}", rule.getId(), e.getMessage(), e);
        }
    }

    private String resolveWebhookUrl(TenantNotificationRule rule) {
        if (rule.getChannelConfig() == null || rule.getChannelConfig().isBlank()) {
            return null;
        }
        try {
            JsonNode config = objectMapper.readTree(rule.getChannelConfig());
            if (config.has("webhook_url")) {
                return config.get("webhook_url").asText();
            }
        } catch (Exception e) {
            log.error("Failed to parse channel_config for rule {}: {}", rule.getId(), e.getMessage());
        }
        return null;
    }

    private String buildSlackMessage(NotificationContext context, TenantNotificationRule rule) {
        ProcessedEmail email = context.getEmail();
        NotificationEvent event = rule.getEvent();
        StringBuilder sb = new StringBuilder();

        switch (event) {
            case EMAIL_RECEIVED:
                sb.append("*Email Received*\n");
                sb.append("From: ").append(email.getFromAddress()).append("\n");
                sb.append("Subject: ").append(email.getSubject()).append("\n");
                sb.append("Attachments: ").append(email.getTotalAttachments()).append("\n");
                sb.append("Correlation ID: `").append(email.getCorrelationId()).append("`");
                break;

            case EXTRACTION_COMPLETED:
                sb.append("*Extraction Completed*\n");
                sb.append("From: ").append(email.getFromAddress()).append("\n");
                sb.append("Subject: ").append(email.getSubject()).append("\n");
                if (context.getTasks() != null) {
                    long completed = context.getTasks().stream()
                            .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED).count();
                    long failed = context.getTasks().stream()
                            .filter(t -> t.getStatus() == ExtractionStatus.FAILED).count();
                    sb.append("Results: ").append(completed).append(" completed, ")
                            .append(failed).append(" failed / ")
                            .append(context.getTasks().size()).append(" total\n");
                }
                sb.append("Correlation ID: `").append(email.getCorrelationId()).append("`");
                break;

            case WEBHOOK_CALLBACK:
                sb.append("*Webhook Callback Received*\n");
                sb.append("From: ").append(email.getFromAddress()).append("\n");
                sb.append("Subject: ").append(email.getSubject()).append("\n");
                if (context.getCallbackResponse() != null) {
                    WebhookCallbackResponse cb = context.getCallbackResponse();
                    sb.append("Status: ").append(cb.getStatus()).append("\n");
                    if (cb.getReference() != null) {
                        sb.append("Reference: ").append(cb.getReference()).append("\n");
                    }
                    if (cb.getMessage() != null) {
                        sb.append("Message: ").append(cb.getMessage()).append("\n");
                    }
                }
                sb.append("Correlation ID: `").append(email.getCorrelationId()).append("`");
                break;

            default:
                sb.append("Notification: ").append(event.name());
                break;
        }

        return sb.toString();
    }
}
