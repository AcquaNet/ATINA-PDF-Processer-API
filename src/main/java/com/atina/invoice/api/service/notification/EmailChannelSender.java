package com.atina.invoice.api.service.notification;

import com.atina.invoice.api.config.EmailConfig;
import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.atina.invoice.api.model.enums.NotificationChannel;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.atina.invoice.api.model.enums.NotificationRecipientType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelSender implements NotificationChannelSender {

    private final JavaMailSender mailSender;
    private final Mustache.Compiler mustacheCompiler;
    private final EmailConfig emailConfig;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationContext context, TenantNotificationRule rule) {
        try {
            String templateName = rule.getTemplateName();
            if (templateName == null || templateName.isBlank()) {
                log.debug("No template configured for rule {}, skipping email notification", rule.getId());
                return;
            }

            Map<String, Object> vars = buildTemplateVars(context, rule);
            String templateContent = loadTemplate(context.getTenant(), templateName);
            String htmlBody = renderTemplate(templateContent, vars);

            String to = resolveRecipient(context, rule);
            if (to == null || to.isBlank()) {
                log.warn("No recipient resolved for rule {}, skipping", rule.getId());
                return;
            }

            String subject = rule.getSubjectTemplate() != null ? rule.getSubjectTemplate() : "Notification";

            // Render subject if it contains mustache tags
            if (subject.contains("{{")) {
                subject = renderTemplate(subject, vars);
            }

            sendEmail(to, null, subject, htmlBody);

            log.info("Email notification sent to {} for event {}", to, rule.getEvent());

        } catch (Exception e) {
            log.error("Failed to send email notification for rule {}: {}", rule.getId(), e.getMessage(), e);
        }
    }

    private String resolveRecipient(NotificationContext context, TenantNotificationRule rule) {
        if (rule.getRecipientType() == NotificationRecipientType.SENDER) {
            return context.getEmail() != null ? context.getEmail().getFromAddress() : null;
        }

        // TENANT_USER: read email from channel_config
        if (rule.getChannelConfig() != null && !rule.getChannelConfig().isBlank()) {
            try {
                JsonNode config = objectMapper.readTree(rule.getChannelConfig());
                if (config.has("email")) {
                    return config.get("email").asText();
                }
            } catch (Exception e) {
                log.error("Failed to parse channel_config for rule {}: {}", rule.getId(), e.getMessage());
            }
        }
        return null;
    }

    private Map<String, Object> buildTemplateVars(NotificationContext context, TenantNotificationRule rule) {
        NotificationEvent event = rule.getEvent();

        switch (event) {
            case EMAIL_RECEIVED:
                return buildReceivedEmailVars(context);
            case EXTRACTION_COMPLETED:
                return buildProcessedEmailVars(context);
            case WEBHOOK_CALLBACK:
                return buildCallbackVars(context);
            default:
                return new HashMap<>();
        }
    }

    private Map<String, Object> buildReceivedEmailVars(NotificationContext context) {
        Map<String, Object> vars = new HashMap<>();
        ProcessedEmail email = context.getEmail();

        vars.put("emailId", email.getId());
        vars.put("correlationId", email.getCorrelationId() != null ? email.getCorrelationId() : "N/A");
        vars.put("subject", email.getSubject());
        vars.put("fromAddress", email.getFromAddress());
        vars.put("receivedAt", formatInstant(email.getReceivedDate()));
        vars.put("tenantName", context.getTenant().getTenantName());
        vars.put("tenantCode", context.getTenant().getTenantCode());

        int totalAttachments = email.getTotalAttachments() != null ? email.getTotalAttachments() : 0;
        int processedAttachments = email.getProcessedAttachments() != null ? email.getProcessedAttachments() : 0;
        vars.put("totalAttachments", totalAttachments);
        vars.put("processedAttachments", processedAttachments);
        vars.put("hasRejected", totalAttachments > processedAttachments);

        List<Map<String, Object>> attachmentsList = new ArrayList<>();
        if (context.getAttachments() != null) {
            for (ProcessedAttachment att : context.getAttachments()) {
                if (att.getProcessingStatus().name().equals("DOWNLOADED")) {
                    Map<String, Object> attMap = new HashMap<>();
                    attMap.put("filename", att.getOriginalFilename());
                    attMap.put("size bytes", formatFileSize(att.getFileSizeBytes()));
                    attachmentsList.add(attMap);
                }
            }
        }
        vars.put("attachments", attachmentsList);

        return vars;
    }

    private Map<String, Object> buildProcessedEmailVars(NotificationContext context) {
        Map<String, Object> vars = new HashMap<>();
        ProcessedEmail email = context.getEmail();
        List<ExtractionTask> tasks = context.getTasks();

        vars.put("emailId", email.getId());
        vars.put("correlationId", email.getCorrelationId() != null ? email.getCorrelationId() : "N/A");
        vars.put("subject", email.getSubject());
        vars.put("fromAddress", email.getFromAddress());
        vars.put("tenantName", context.getTenant().getTenantName());
        vars.put("tenantCode", context.getTenant().getTenantCode());

        if (tasks != null) {
            long totalFiles = tasks.size();
            long extractedFiles = tasks.stream()
                    .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                    .count();
            long failedFiles = tasks.stream()
                    .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                    .count();
            int successRate = totalFiles > 0 ? (int) ((extractedFiles * 100) / totalFiles) : 0;

            vars.put("totalFiles", totalFiles);
            vars.put("extractedFiles", extractedFiles);
            vars.put("failedFiles", failedFiles);
            vars.put("successRate", successRate);
            vars.put("isFullSuccess", extractedFiles == totalFiles && totalFiles > 0);
            vars.put("hasFailures", failedFiles > 0);
            vars.put("hasSuccesses", extractedFiles > 0);

            List<Map<String, Object>> extractions = tasks.stream()
                    .map(this::taskToEmailSummary)
                    .collect(Collectors.toList());
            vars.put("extractions", extractions);
        }

        return vars;
    }

    private Map<String, Object> buildCallbackVars(NotificationContext context) {
        Map<String, Object> vars = new HashMap<>();
        ProcessedEmail email = context.getEmail();

        vars.put("emailId", email.getId());
        vars.put("correlationId", email.getCorrelationId() != null ? email.getCorrelationId() : "N/A");
        vars.put("subject", email.getSubject());
        vars.put("fromAddress", email.getFromAddress());
        vars.put("tenantName", context.getTenant().getTenantName());
        vars.put("tenantCode", context.getTenant().getTenantCode());

        WebhookCallbackResponse callback = context.getCallbackResponse();
        if (callback != null) {
            vars.put("callbackStatus", callback.getStatus());
            vars.put("callbackReference", callback.getReference() != null ? callback.getReference() : "N/A");
            vars.put("callbackMessage", callback.getMessage() != null ? callback.getMessage() : "");
            vars.put("callbackReceivedAt", formatInstant(callback.getReceivedAt()));
        }

        if (context.getTasks() != null) {
            long totalFiles = context.getTasks().size();
            long extractedFiles = context.getTasks().stream()
                    .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                    .count();
            vars.put("totalFiles", totalFiles);
            vars.put("extractedFiles", extractedFiles);
        }

        return vars;
    }

    private Map<String, Object> taskToEmailSummary(ExtractionTask task) {
        Map<String, Object> summary = new HashMap<>();

        summary.put("filename", task.getAttachment().getOriginalFilename());
        summary.put("status", task.getStatus().name());
        summary.put("success", task.getStatus() == ExtractionStatus.COMPLETED);

        if (task.getStatus() == ExtractionStatus.COMPLETED) {
            summary.put("resultPath", task.getResultPath());

            if (task.getRawResult() != null && !task.getRawResult().isBlank()) {
                try {
                    JsonNode result = objectMapper.readTree(task.getRawResult());
                    Map<String, Object> invoiceData = extractInvoiceData(result);
                    summary.put("hasInvoiceData", !invoiceData.isEmpty());
                    summary.putAll(invoiceData);
                } catch (Exception e) {
                    log.debug("Could not parse invoice data for task {}: {}", task.getId(), e.getMessage());
                    summary.put("hasInvoiceData", false);
                }
            } else {
                summary.put("hasInvoiceData", false);
            }
        } else {
            summary.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "Unknown error");
        }

        return summary;
    }

    private Map<String, Object> extractInvoiceData(JsonNode result) {
        Map<String, Object> data = new HashMap<>();

        if (result.has("invoice_number")) data.put("invoiceNumber", result.get("invoice_number").asText());
        if (result.has("invoiceNumber")) data.put("invoiceNumber", result.get("invoiceNumber").asText());
        if (result.has("invoice_date")) data.put("invoiceDate", result.get("invoice_date").asText());
        if (result.has("invoiceDate")) data.put("invoiceDate", result.get("invoiceDate").asText());
        if (result.has("total")) data.put("total", result.get("total").asText());
        if (result.has("amount")) data.put("total", result.get("amount").asText());
        if (result.has("currency")) data.put("currency", result.get("currency").asText());
        if (result.has("vendor")) data.put("vendor", result.get("vendor").asText());
        if (result.has("supplier")) data.put("vendor", result.get("supplier").asText());

        return data;
    }

    private String loadTemplate(Tenant tenant, String templateName) throws IOException {
        String basePath = tenant.getStorageBasePath();
        String tenantCode = tenant.getTenantCode();
        String templatePath = String.format("%s/%s/config/email-templates/%s",
                basePath, tenantCode, templateName);

        log.debug("Loading email template from: {}", templatePath);

        Path path = Paths.get(templatePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Email template not found: " + templatePath);
        }

        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String renderTemplate(String templateContent, Map<String, Object> vars) {
        try {
            com.samskivert.mustache.Template template = mustacheCompiler.compile(templateContent);
            return template.execute(vars);
        } catch (MustacheException e) {
            log.error("Failed to render template: {}", e.getMessage(), e);
            throw new RuntimeException("Template rendering failed: " + e.getMessage(), e);
        }
    }

    private void sendEmail(String to, String cc, String subject, String htmlBody) throws MessagingException {
        if (!emailConfig.isEnabled()) {
            log.info("Email notifications disabled in config, skipping send to {}", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailConfig.getFrom(), emailConfig.getFromName());
            helper.setReplyTo(emailConfig.getReplyTo());
            helper.setTo(to);

            if (cc != null && !cc.isBlank()) {
                helper.setCc(cc);
            }

            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            log.info("Email sent to {} (subject: {})", to, subject);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new MessagingException("Email sending failed: " + e.getMessage(), e);
        }
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "N/A";
        return DATE_FORMATTER.format(instant);
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
