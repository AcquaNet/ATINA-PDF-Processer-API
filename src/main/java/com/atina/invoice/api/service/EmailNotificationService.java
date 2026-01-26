package com.atina.invoice.api.service;

import com.atina.invoice.api.model.EmailSenderRule;
import com.atina.invoice.api.model.ExtractionTask;
import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.model.enums.ExtractionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio para enviar notificaciones por email
 *
 * Envía emails en dos momentos:
 * 1. Cuando se recibe un email (templateEmailReceived)
 * 2. Cuando se completa el procesamiento (templateEmailProcessed)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final ObjectMapper objectMapper;
    // TODO: Inject JavaMailSender cuando esté configurado
    // private final JavaMailSender mailSender;

    /**
     * Enviar email de recepción (templateEmailReceived)
     * Se llama desde EmailPollingService después de guardar el email
     *
     * @param email Email procesado
     */
    @Async
    public void sendReceivedEmail(ProcessedEmail email) {
        EmailSenderRule rule = email.getSenderRule();

        if (rule == null) {
            log.debug("No sender rule for email {}, skipping received notification", email.getId());
            return;
        }

        if (!rule.getAutoReplyEnabled()) {
            log.debug("Auto-reply disabled for sender {}, skipping", rule.getSenderEmail());
            return;
        }

        if (rule.getTemplateEmailReceived() == null || rule.getTemplateEmailReceived().isBlank()) {
            log.debug("No received email template configured, skipping");
            return;
        }

        log.info("📧 Sending received email notification for email {}", email.getId());

        try {
            Map<String, Object> vars = buildReceivedEmailVars(email);
            String html = renderTemplate(rule.getTemplateEmailReceived(), vars);

            String subject = String.format("Email recibido - ID: %d", email.getId());

            // Enviar a sender
            sendEmail(rule.getSenderEmail(), subject, html);

            // Enviar a notificationEmail si existe
            if (rule.getNotificationEmail() != null && !rule.getNotificationEmail().isBlank()) {
                sendEmail(rule.getNotificationEmail(), subject, html);
            }

            log.info("✅ Received email notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send received email notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Enviar email de procesamiento completado (templateEmailProcessed)
     * Se llama desde ExtractionWorker cuando todas las tareas completan
     *
     * @param email Email procesado
     * @param tasks Tareas de extracción
     */
    @Async
    public void sendProcessedEmail(ProcessedEmail email, List<ExtractionTask> tasks) {
        EmailSenderRule rule = email.getSenderRule();

        if (rule == null) {
            log.debug("No sender rule for email {}, skipping processed notification", email.getId());
            return;
        }

        if (!rule.getProcessEnabled()) {
            log.debug("Processing disabled for sender {}, skipping", rule.getSenderEmail());
            return;
        }

        if (rule.getTemplateEmailProcessed() == null || rule.getTemplateEmailProcessed().isBlank()) {
            log.debug("No processed email template configured, skipping");
            return;
        }

        log.info("📧 Sending processed email notification for email {}", email.getId());

        try {
            Map<String, Object> vars = buildProcessedEmailVars(email, tasks);
            String html = renderTemplate(rule.getTemplateEmailProcessed(), vars);

            String subject = String.format("PDFs procesados - ID: %d", email.getId());

            // Enviar a sender
            sendEmail(rule.getSenderEmail(), subject, html);

            // Enviar a notificationEmail si existe
            if (rule.getNotificationEmail() != null && !rule.getNotificationEmail().isBlank()) {
                sendEmail(rule.getNotificationEmail(), subject, html);
            }

            log.info("✅ Processed email notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send processed email notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Construir variables para template de email recibido
     */
    private Map<String, Object> buildReceivedEmailVars(ProcessedEmail email) {
        Map<String, Object> vars = new HashMap<>();

        vars.put("email_id", email.getId());
        vars.put("correlation_id", email.getCorrelationId());
        vars.put("subject", email.getSubject());
        vars.put("from_address", email.getFromAddress());
        vars.put("total_attachments", email.getTotalAttachments());
        vars.put("processed_attachments", email.getProcessedAttachments());
        vars.put("received_at", email.getCreatedAt() != null
                ? email.getCreatedAt().toString()
                : "");

        // Información del tenant
        if (email.getTenant() != null) {
            vars.put("tenant_name", email.getTenant().getTenantName());
            vars.put("tenant_code", email.getTenant().getTenantCode());
        }

        return vars;
    }

    /**
     * Construir variables para template de email procesado
     */
    private Map<String, Object> buildProcessedEmailVars(ProcessedEmail email, List<ExtractionTask> tasks) {
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.COMPLETED)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == ExtractionStatus.FAILED)
                .count();

        Map<String, Object> vars = new HashMap<>();

        vars.put("email_id", email.getId());
        vars.put("correlation_id", email.getCorrelationId());
        vars.put("subject", email.getSubject());
        vars.put("from_address", email.getFromAddress());
        vars.put("total_files", tasks.size());
        vars.put("extracted_files", completed);
        vars.put("failed_files", failed);
        vars.put("success_rate", tasks.size() > 0
                ? (completed * 100.0 / tasks.size())
                : 0.0);

        // Lista de extracciones con resumen
        vars.put("extractions", tasks.stream()
                .map(this::taskToEmailSummary)
                .collect(Collectors.toList()));

        // Información del tenant
        if (email.getTenant() != null) {
            vars.put("tenant_name", email.getTenant().getTenantName());
            vars.put("tenant_code", email.getTenant().getTenantCode());
        }

        return vars;
    }

    /**
     * Convertir task a resumen para email
     */
    private Map<String, Object> taskToEmailSummary(ExtractionTask task) {
        Map<String, Object> summary = new HashMap<>();

        summary.put("task_id", task.getId());
        summary.put("filename", task.getAttachment().getOriginalFilename());
        summary.put("source", task.getSource());
        summary.put("status", task.getStatus().name());
        summary.put("status_display", getStatusDisplay(task.getStatus()));

        // Si completó, incluir campos principales del resultado
        if (task.getStatus() == ExtractionStatus.COMPLETED && task.getRawResult() != null) {
            try {
                Map<String, Object> data = objectMapper.readValue(
                        task.getRawResult(),
                        new TypeReference<Map<String, Object>>() {}
                );

                // Extraer campos comunes de facturas (ajustar según template)
                summary.put("invoice_number", data.get("invoice_number"));
                summary.put("invoice_date", data.get("invoice_date"));
                summary.put("total_amount", data.get("total_amount"));
                summary.put("vendor_name", data.get("vendor_name"));
                summary.put("currency", data.get("currency"));

                // Incluir resultado completo para templates avanzados
                summary.put("full_data", data);

            } catch (Exception e) {
                log.warn("Failed to parse extraction data for task {}", task.getId(), e);
                summary.put("parse_error", true);
            }
        }

        // Si falló, incluir error
        if (task.getStatus() == ExtractionStatus.FAILED) {
            summary.put("error", task.getErrorMessage());
        }

        return summary;
    }

    /**
     * Obtener texto legible del estado
     */
    private String getStatusDisplay(ExtractionStatus status) {
        return switch (status) {
            case COMPLETED -> "✅ Completado";
            case FAILED -> "❌ Fallido";
            case PROCESSING -> "🔄 Procesando";
            case PENDING -> "⏳ Pendiente";
            case RETRYING -> "🔁 Reintentando";
            case CANCELLED -> "🚫 Cancelado";
        };
    }

    /**
     * Renderizar template HTML con variables
     *
     * TODO: Implementar usando sistema de templates (Thymeleaf, Freemarker, etc)
     *
     * Por ahora, simple reemplazo de placeholders
     */
    private String renderTemplate(String template, Map<String, Object> vars) {
        String result = template;

        // Reemplazar placeholders ${variable}
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }

        // Para listas (como extractions), generar HTML básico
        if (vars.containsKey("extractions") && vars.get("extractions") instanceof List) {
            List<?> extractions = (List<?>) vars.get("extractions");
            StringBuilder extractionsHtml = new StringBuilder();

            for (Object extraction : extractions) {
                if (extraction instanceof Map) {
                    Map<?, ?> ext = (Map<?, ?>) extraction;
                    extractionsHtml.append("<li>")
                            .append(ext.get("filename")).append(" - ")
                            .append(ext.get("status_display"))
                            .append("</li>");
                }
            }

            result = result.replace("${extractions_list}", extractionsHtml.toString());
        }

        return result;
    }

    /**
     * Enviar email
     *
     * TODO: Implementar usando JavaMailSender
     *
     * @param to Destinatario
     * @param subject Asunto
     * @param htmlBody Cuerpo HTML
     */
    private void sendEmail(String to, String subject, String htmlBody) {
        // TODO: Implementar envío real de email cuando se configure JavaMailSender
        log.info("📧 [EMAIL-MOCK] Would send email to: {}", to);
        log.debug("Subject: {}", subject);
        log.debug("Body (first 100 chars): {}...",
                htmlBody.length() > 100 ? htmlBody.substring(0, 100) : htmlBody);

        /*
        // Ejemplo de implementación con JavaMailSender:
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML
            helper.setFrom("noreply@yourdomain.com");

            mailSender.send(message);

            log.info("Email sent successfully to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
        */
    }
}
