package com.atina.invoice.api.service;

import com.atina.invoice.api.model.AttachmentProcessingRule;
import com.atina.invoice.api.model.EmailSenderRule;
import com.atina.invoice.api.model.ProcessedEmail;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Helpers para EmailProcessingService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailProcessingHelpers {

    /**
     * Buscar regla que matchee el nombre del archivo
     */
    public Optional<AttachmentProcessingRule> findMatchingRule(
            EmailSenderRule senderRule, String filename) {

        // Obtener reglas ordenadas por prioridad
        List<AttachmentProcessingRule> rules = senderRule.getAttachmentRules().stream()
                .filter(AttachmentProcessingRule::getEnabled)
                .sorted(Comparator.comparing(AttachmentProcessingRule::getRuleOrder))
                .toList();

        // Buscar primera regla que matchee
        for (AttachmentProcessingRule rule : rules) {
            try {
                Pattern pattern = Pattern.compile(rule.getFileNameRegex());
                if (pattern.matcher(filename).matches()) {
                    log.debug("File '{}' matched rule #{}: {}", 
                            filename, rule.getRuleOrder(), rule.getFileNameRegex());
                    return Optional.of(rule);
                }
            } catch (Exception e) {
                log.error("Error compiling regex for rule {}: {}", 
                        rule.getId(), e.getMessage());
            }
        }

        log.info("No rule matched for file: {}", filename);
        return Optional.empty();
    }

    /**
     * Generar metadata JSON del email
     */
    public Map<String, Object> generateMetadata(ProcessedEmail processedEmail) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // Email info
        metadata.put("email_id", processedEmail.getId());
        metadata.put("email_uid", processedEmail.getEmailUid());
        metadata.put("subject", processedEmail.getSubject());
        metadata.put("from", processedEmail.getFromAddress());
        metadata.put("from_full", processedEmail.getFromAddresses());
        metadata.put("to", parseJsonArray(processedEmail.getToAddresses()));
        metadata.put("cc", parseJsonArray(processedEmail.getCcAddresses()));
        metadata.put("bcc", parseJsonArray(processedEmail.getBccAddresses()));

        // Dates
        metadata.put("sent_date", processedEmail.getSentDate());
        metadata.put("received_date", processedEmail.getReceivedDate());
        metadata.put("processed_date", processedEmail.getProcessedDate());

        // Sender rule info
        if (processedEmail.getSenderRule() != null) {
            EmailSenderRule rule = processedEmail.getSenderRule();
            Map<String, Object> senderInfo = new LinkedHashMap<>();
            senderInfo.put("sender_id", rule.getSenderId());
            senderInfo.put("sender_name", rule.getSenderName());
            senderInfo.put("sender_email", rule.getSenderEmail());
            metadata.put("sender_rule", senderInfo);
        }

        // Attachments info
        metadata.put("total_attachments", processedEmail.getTotalAttachments());
        metadata.put("processed_attachments", processedEmail.getProcessedAttachments());

        // Attachments details
        List<Map<String, Object>> attachmentsList = new ArrayList<>();
        for (int i = 0; i < processedEmail.getAttachments().size(); i++) {
            var attachment = processedEmail.getAttachments().get(i);

            Map<String, Object> attachmentInfo = new LinkedHashMap<>();
            attachmentInfo.put("sequence", i + 1);
            attachmentInfo.put("original_filename", attachment.getOriginalFilename());
            attachmentInfo.put("normalized_filename", attachment.getNormalizedFilename());
            attachmentInfo.put("file_path", attachment.getFilePath());
            attachmentInfo.put("mime_type", attachment.getMimeType());
            attachmentInfo.put("file_size", attachment.getFileSizeBytes());
            attachmentInfo.put("processing_status", attachment.getProcessingStatus());

            if (attachment.getRule() != null) {
                AttachmentProcessingRule rule = attachment.getRule();
                Map<String, Object> ruleInfo = new LinkedHashMap<>();
                ruleInfo.put("rule_id", rule.getId());
                ruleInfo.put("rule_order", rule.getRuleOrder());
                ruleInfo.put("regex", rule.getFileNameRegex());
                ruleInfo.put("source", rule.getSource());
                ruleInfo.put("destination", rule.getDestination());
                ruleInfo.put("processing_method", rule.getProcessingMethod());
                attachmentInfo.put("rule", ruleInfo);
            }

            attachmentsList.add(attachmentInfo);
        }
        metadata.put("attachments", attachmentsList);

        // Processing info
        metadata.put("status", processedEmail.getProcessingStatus());
        metadata.put("error_message", processedEmail.getErrorMessage());

        return metadata;
    }

    /**
     * Convertir lista a JSON string
     */
    public String convertListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Error converting list to JSON", e);
            return "[]";
        }
    }

    /**
     * Parsear JSON array string a lista
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON array", e);
            return Collections.emptyList();
        }
    }

    /**
     * Extraer filename de un path
     */
    public String extractFilename(String filePath) {
        if (filePath == null) {
            return null;
        }
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            return filePath.substring(lastSlash + 1);
        }
        return filePath;
    }
}
