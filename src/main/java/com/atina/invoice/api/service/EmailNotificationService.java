package com.atina.invoice.api.service;

import com.atina.invoice.api.config.EmailConfig;
import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.ExtractionStatus;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

/**
 * Servicio para enviar notificaciones por email usando JMustache templates
 *
 * Envía emails en dos momentos:
 * 1. Cuando se recibe un email y se descargan PDFs (templateEmailReceived)
 * 2. Cuando se completa el procesamiento de todos los PDFs (templateEmailProcessed)
 *
 * Templates:
 * - Location: {storageBasePath}/{tenantCode}/config/email-templates/{templateName}
 * - Format: Mustache (.mustache)
 * - Rendering: JMustache library
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final Mustache.Compiler mustacheCompiler;
    private final EmailConfig emailConfig;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Enviar email de recepción (templateEmailReceived)
     * Se llama desde EmailProcessingService después de guardar el email
     *
     * @param email Email procesado
     */
    @Async
    public void sendReceivedEmail(ProcessedEmail email, List<ProcessedAttachment> attachments) {
        EmailSenderRule rule = email.getSenderRule();

        if (rule == null) {
            log.debug("No sender rule for email {}, skipping received notification", email.getId());
            return;
        }

        if (!rule.getAutoReplyEnabled()) {
            log.debug("Auto-reply disabled for rule {}, skipping received notification", rule.getId());
            return;
        }

        if (rule.getTemplateEmailReceived() == null || rule.getTemplateEmailReceived().isBlank()) {
            log.debug("No template configured for received notification, skipping");
            return;
        }

        try {
            log.info("Sending received email notification for email {} (tenant: {})",
                    email.getId(), email.getTenant().getTenantCode());

            // Build template variables
            Map<String, Object> vars = buildReceivedEmailVars(email,attachments);

            // Load and render template
            String templateContent = loadTemplate(email.getTenant(), rule.getTemplateEmailReceived());
            String htmlBody = renderTemplate(templateContent, vars);

            // Send email
            String to = email.getFromAddress();
            String cc = rule.getNotificationEmail();
            String subject = rule.getSubjectEmailReceived();

            sendEmail(to, cc, subject, htmlBody);

            log.info("✅ Received email notification sent successfully to {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send received email notification for email {}: {}",
                    email.getId(), e.getMessage(), e);
            // Don't throw - @Async method, just log error
        }
    }

    /**
     * Enviar email de procesamiento completado (templateEmailProcessed)
     * Se llama desde ExtractionWorker cuando todas las tareas terminan
     *
     * @param email Email procesado
     * @param tasks Lista de tareas de extracción
     */
    @Async
    public void sendProcessedEmail(ProcessedEmail email, List<ExtractionTask> tasks) {
        EmailSenderRule rule = email.getSenderRule();

        if (rule == null) {
            log.debug("No sender rule for email {}, skipping processed notification", email.getId());
            return;
        }

        if (!rule.getProcessEnabled()) {
            log.debug("Process notification disabled for rule {}, skipping", rule.getId());
            return;
        }

        if (rule.getTemplateEmailProcessed() == null || rule.getTemplateEmailProcessed().isBlank()) {
            log.debug("No template configured for processed notification, skipping");
            return;
        }

        try {

            log.info("Sending processed email notification for email {} (tenant: {}, tasks: {})",
                    email.getId(), email.getTenant().getTenantCode(), tasks.size());

            // Build template variables
            Map<String, Object> vars = buildProcessedEmailVars(email, tasks);

            log.info("Template vars for processed email (emailId={}, tenant={}): keys={}, vars={}",
                    email.getId(),
                    email.getTenant().getTenantCode(),
                    vars != null ? vars.keySet() : null,
                    vars
            );

            // Load and render template
            String templateContent = loadTemplate(email.getTenant(), rule.getTemplateEmailProcessed());
            String htmlBody = renderTemplate(templateContent, vars);

            // Send email
            String to = email.getFromAddress();
            String cc = rule.getNotificationEmail();
            String subject = rule.getSubjectEmailProcessed();

            sendEmail(to, cc, subject, htmlBody);

            log.info("✅ Processed email notification sent successfully to {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send processed email notification for email {}: {}",
                    email.getId(), e.getMessage(), e);
            // Don't throw - @Async method, just log error
        }
    }

    /**
     * Build template variables for received email notification
     */
    private Map<String, Object> buildReceivedEmailVars(ProcessedEmail email, List<ProcessedAttachment> attachments) {
        Map<String, Object> vars = new HashMap<>();

        // Email info
        vars.put("emailId", email.getId());
        vars.put("correlationId", email.getCorrelationId() != null ? email.getCorrelationId() : "N/A");
        vars.put("subject", email.getSubject());
        vars.put("fromAddress", email.getFromAddress());
        vars.put("receivedAt", formatInstant(email.getReceivedDate()));

        // Tenant info
        vars.put("tenantName", email.getTenant().getTenantName());
        vars.put("tenantCode", email.getTenant().getTenantCode());

        // Attachments info
        int totalAttachments = email.getTotalAttachments() != null ? email.getTotalAttachments() : 0;
        int processedAttachments = email.getProcessedAttachments() != null ? email.getProcessedAttachments() : 0;

        vars.put("totalAttachments", totalAttachments);
        vars.put("processedAttachments", processedAttachments);
        vars.put("hasRejected", totalAttachments > processedAttachments);

        // Build attachments list for template
        List<Map<String, Object>> attachmentsList = new ArrayList<>();
        if (attachments != null) {
            for (ProcessedAttachment att : attachments) {
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

    /**
     * Build template variables for processed email notification
     */
    private Map<String, Object> buildProcessedEmailVars(ProcessedEmail email, List<ExtractionTask> tasks) {
        Map<String, Object> vars = new HashMap<>();

        // Email info
        vars.put("emailId", email.getId());
        vars.put("correlationId", email.getCorrelationId() != null ? email.getCorrelationId() : "N/A");
        vars.put("subject", email.getSubject());
        vars.put("fromAddress", email.getFromAddress());

        // Tenant info
        vars.put("tenantName", email.getTenant().getTenantName());
        vars.put("tenantCode", email.getTenant().getTenantCode());

        // Processing stats
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

        // Success/failure flags
        vars.put("isFullSuccess", extractedFiles == totalFiles && totalFiles > 0);
        vars.put("hasFailures", failedFiles > 0);
        vars.put("hasSuccesses", extractedFiles > 0);

        // Build extractions list
        List<Map<String, Object>> extractions = tasks.stream()
                .map(this::taskToEmailSummary)
                .collect(Collectors.toList());
        vars.put("extractions", extractions);

        return vars;
    }

    /**
     * Convert ExtractionTask to email summary map
     */
    private Map<String, Object> taskToEmailSummary(ExtractionTask task) {
        Map<String, Object> summary = new HashMap<>();

        summary.put("filename", task.getAttachment().getOriginalFilename());
        summary.put("status", task.getStatus().name());
        summary.put("success", task.getStatus() == ExtractionStatus.COMPLETED);

        if (task.getStatus() == ExtractionStatus.COMPLETED) {
            summary.put("resultPath", task.getResultPath());

            // Try to parse invoice data from rawResult
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

    /**
     * Extract common invoice fields from extraction result
     */
    private Map<String, Object> extractInvoiceData(JsonNode result) {
        Map<String, Object> data = new HashMap<>();

        // Try common field names
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

    /**
     * Load template from tenant-specific directory
     * Path: {storageBasePath}/{tenantCode}/config/email-templates/{templateName}
     */
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

    /**
     * Render mustache template with variables
     */
    private String renderTemplate(String templateContent, Map<String, Object> vars) {
        try {
            log.debug("Rendering template with {} variables", vars.size());
            com.samskivert.mustache.Template template = mustacheCompiler.compile(templateContent);
            return template.execute(vars);
        } catch (MustacheException e) {
            log.error("Failed to render template: {}", e.getMessage(), e);
            throw new RuntimeException("Template rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send HTML email using Spring Mail
     */
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
                log.debug("Adding CC: {}", cc);
            }

            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);

            log.info("📧 Email sent successfully to {} (subject: {})", to, subject);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new MessagingException("Email sending failed: " + e.getMessage(), e);
        }
    }

    /**
     * Format Instant to readable date string
     */
    private String formatInstant(Instant instant) {
        if (instant == null) return "N/A";
        return DATE_FORMATTER.format(instant);
    }

    /**
     * Format file size to human readable string
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) return "";

        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
