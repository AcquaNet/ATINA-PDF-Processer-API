package com.atina.invoice.api.service;

import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.AttachmentProcessingStatus;
import com.atina.invoice.api.model.enums.EmailProcessingStatus;
import com.atina.invoice.api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio principal de procesamiento de emails
 * Coordina la lectura, parsing y almacenamiento de emails y attachments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailProcessingService {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailSenderRuleRepository senderRuleRepository;
    private final ProcessedEmailRepository processedEmailRepository;
    private final ProcessedAttachmentRepository processedAttachmentRepository;
    private final EmailReaderService emailReaderService;
    private final FileStorageService fileStorageService;
    private final EmailProcessingHelpers helpers;
    private final ObjectMapper objectMapper;

    /**
     * Procesar emails de una cuenta específica
     * Este método es llamado por el scheduler
     * 
     * @param emailAccount Cuenta de email a procesar
     * @return Número de emails procesados exitosamente
     */
    @Transactional
    public int processEmailsFromAccount(EmailAccount emailAccount) {
        log.info("🔍 Processing emails from: {}", emailAccount.getEmailAddress());

        try {

            // ----------------------------------------------
            // Leer emails nuevos desde el servidor
            // ----------------------------------------------

            List<EmailReaderService.EmailMessage> newEmails = 
                    emailReaderService.readNewEmails(emailAccount);

            if (newEmails.isEmpty()) {
                log.info("✓ No new emails in {}", emailAccount.getEmailAddress());
                updateAccountPollDates(emailAccount, true);
                return 0;
            }

            log.info("📧 Found {} new emails in {}", newEmails.size(), emailAccount.getEmailAddress());

            // ----------------------------------------------
            // Procesar cada email
            // ----------------------------------------------

            int processedCount = 0;
            String lastProcessedUid = emailAccount.getLastProcessedUid();

            for (EmailReaderService.EmailMessage emailMessage : newEmails) {
                try {
                    // Verificar si ya fue procesado
                    if (processedEmailRepository.existsByEmailAccountIdAndEmailUid(
                            emailAccount.getId(), emailMessage.uid)) {
                        log.debug("⏭️ Email {} already processed, skipping", emailMessage.uid);
                        continue;
                    }

                    // Procesar email individual
                    processEmail(emailAccount, emailMessage);
                    processedCount++;
                    lastProcessedUid = emailMessage.uid;

                } catch (Exception e) {
                    log.error("❌ Error processing email {}: {}", 
                            emailMessage.uid, e.getMessage(), e);
                }
            }

            // 3. Actualizar última fecha y UID procesado
            emailAccount.setLastProcessedUid(lastProcessedUid);
            updateAccountPollDates(emailAccount, true);

            log.info("✅ Successfully processed {} emails from {}", 
                    processedCount, emailAccount.getEmailAddress());

            return processedCount;

        } catch (Exception e) {
            log.error("❌ Error processing emails from {}: {}", 
                    emailAccount.getEmailAddress(), e.getMessage(), e);
            updateAccountPollDates(emailAccount, false);
            return 0;
        }
    }

    /**
     * Procesar un email individual
     */
    private void processEmail(
            EmailAccount emailAccount, 
            EmailReaderService.EmailMessage emailMessage) {
        
        log.debug("📨 Processing email from {}: {}", 
                emailMessage.fromAddress, emailMessage.subject);

        // 1. Buscar regla de sender
        Optional<EmailSenderRule> senderRuleOpt = senderRuleRepository
                .findByEmailAccountIdAndSenderEmail(
                        emailAccount.getId(), 
                        emailMessage.fromAddress);

        // 2. Crear registro de email procesado
        ProcessedEmail processedEmail = buildProcessedEmail(
                emailAccount, emailMessage, senderRuleOpt.orElse(null));

        // 3. Validar si debe procesarse
        if (senderRuleOpt.isEmpty()) {
            log.info("⏭️ No sender rule for {}, marking as IGNORED", emailMessage.fromAddress);
            processedEmail.markAsIgnored();
            processedEmailRepository.save(processedEmail);
            return;
        }

        EmailSenderRule senderRule = senderRuleOpt.get();

        if (!senderRule.getProcessEnabled()) {
            log.info("⏭️ Processing disabled for {}, marking as IGNORED", emailMessage.fromAddress);
            processedEmail.markAsIgnored();
            processedEmailRepository.save(processedEmail);
            return;
        }

        // 4. Guardar email en DB
        processedEmail = processedEmailRepository.save(processedEmail);

        try {
            // 5. Procesar attachments
            int processedCount = processAttachments(
                    processedEmail, senderRule, emailMessage.attachments);

            // 6. Generar metadata JSON
            Map<String, Object> metadata = helpers.generateMetadata(processedEmail);
            String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(metadata);

            // 7. Guardar metadata en archivo
            String metadataPath = fileStorageService.saveEmailMetadata(
                    emailAccount.getTenant(),
                    senderRule.getSenderId(),
                    processedEmail.getId(),
                    emailMessage.fromAddress,
                    metadataJson
            );

            // 8. Actualizar email procesado
            processedEmail.setProcessedAttachments(processedCount);
            processedEmail.setMetadataFilePath(metadataPath);
            processedEmail.setRawMetadata("metadata");
            processedEmail.markAsCompleted();
            processedEmailRepository.save(processedEmail);

            log.info("✅ Email {} processed: {} attachments", 
                    emailMessage.uid, processedCount);

        } catch (Exception e) {
            log.error("❌ Error processing email: {}", e.getMessage(), e);
            processedEmail.markAsFailed(e.getMessage());
            processedEmailRepository.save(processedEmail);
        }
    }

    /**
     * Procesar attachments del email
     */
    private int processAttachments(
            ProcessedEmail processedEmail,
            EmailSenderRule senderRule,
            List<EmailReaderService.AttachmentInfo> attachments) {

        int processedCount = 0;
        int sequence = 1;

        log.debug("📎 Processing {} attachments", attachments.size());

        for (EmailReaderService.AttachmentInfo attachmentInfo : attachments) {
            try {
                // 1. Buscar regla que matchee el filename
                Optional<AttachmentProcessingRule> matchingRule = 
                        helpers.findMatchingRule(senderRule, attachmentInfo.filename);

                // 2. Crear registro de attachment
                ProcessedAttachment attachment = buildProcessedAttachment(
                        processedEmail, attachmentInfo, matchingRule.orElse(null));

                // 3. Si no matchea ninguna regla, ignorar
                if (matchingRule.isEmpty()) {
                    log.debug("⏭️ No rule matched for: {}", attachmentInfo.filename);
                    attachment.markAsIgnored();
                    processedAttachmentRepository.save(attachment);
                    continue;
                }

                AttachmentProcessingRule rule = matchingRule.get();

                // 4. Descargar y guardar archivo
                try (InputStream inputStream = attachmentInfo.part.getInputStream()) {
                    
                    String filePath = fileStorageService.saveAttachment(
                            processedEmail.getTenant(),
                            senderRule.getSenderId(),
                            processedEmail.getId(),
                            sequence,
                            rule.getSource(),
                            rule.getDestination(),
                            attachmentInfo.filename,
                            inputStream
                    );

                    long fileSize = fileStorageService.getFileSize(filePath);
                    String normalizedFilename = helpers.extractFilename(filePath);

                    // 5. Actualizar attachment
                    attachment.setNormalizedFilename(normalizedFilename);
                    attachment.markAsDownloaded(filePath, fileSize);
                    processedAttachmentRepository.save(attachment);

                    processedCount++;
                    sequence++;

                    log.info("✅ Saved: {} → {}", attachmentInfo.filename, normalizedFilename);

                } catch (Exception e) {
                    log.error("❌ Error saving {}: {}", attachmentInfo.filename, e.getMessage());
                    attachment.markAsFailed(e.getMessage());
                    processedAttachmentRepository.save(attachment);
                }

            } catch (Exception e) {
                log.error("❌ Error processing attachment: {}", e.getMessage(), e);
            }
        }

        return processedCount;
    }

    /**
     * Construir entidad ProcessedEmail desde EmailMessage
     */
    private ProcessedEmail buildProcessedEmail(
            EmailAccount emailAccount,
            EmailReaderService.EmailMessage emailMessage,
            EmailSenderRule senderRule) {

        return ProcessedEmail.builder()
                .tenant(emailAccount.getTenant())
                .emailAccount(emailAccount)
                .senderRule(senderRule)
                .emailUid(emailMessage.uid)
                .subject(emailMessage.subject)
                .fromAddress(emailMessage.fromAddress)
                .fromAddresses(emailMessage.fromAddresses)
                .toAddresses(helpers.convertListToJson(emailMessage.toAddresses))
                .ccAddresses(helpers.convertListToJson(emailMessage.ccAddresses))
                .bccAddresses(helpers.convertListToJson(emailMessage.bccAddresses))
                .sentDate(emailMessage.sentDate)
                .receivedDate(emailMessage.receivedDate)
                .processingStatus(EmailProcessingStatus.PROCESSING)
                .totalAttachments(emailMessage.attachments.size())
                .processedAttachments(0)
                .build();
    }

    /**
     * Construir entidad ProcessedAttachment desde AttachmentInfo
     */
    private ProcessedAttachment buildProcessedAttachment(
            ProcessedEmail processedEmail,
            EmailReaderService.AttachmentInfo attachmentInfo,
            AttachmentProcessingRule rule) {

        return ProcessedAttachment.builder()
                .processedEmail(processedEmail)
                .rule(rule)
                .originalFilename(attachmentInfo.filename)
                .mimeType(attachmentInfo.mimeType)
                .processingStatus(AttachmentProcessingStatus.PENDING)
                .storageType(processedEmail.getTenant().getStorageType())
                .build();
    }

    /**
     * Actualizar fechas de polling de la cuenta
     */
    private void updateAccountPollDates(EmailAccount account, boolean success) {
        account.setLastPollDate(java.time.Instant.now());
        if (success) {
            account.setLastSuccessfulPoll(java.time.Instant.now());
        }
        emailAccountRepository.save(account);
    }
}
