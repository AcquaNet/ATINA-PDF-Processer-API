package com.atina.invoice.api.service;

import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.AttachmentProcessingStatus;
import com.atina.invoice.api.model.enums.EmailProcessingStatus;
import com.atina.invoice.api.model.enums.StorageType;
import com.atina.invoice.api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Flags;
import jakarta.mail.Part;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio principal de procesamiento de emails
 * Coordina la lectura, parsing y almacenamiento de emails y attachments
 *
 * MODIFICADO: Ahora soporta marcar emails como leídos
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
     * ⭐ NUEVO: Habilitar/deshabilitar marcar como leído
     * Se puede configurar en application.properties:
     * email.mark-as-read=true
     */
    @Value("${email.mark-as-read:true}")
    private boolean markAsRead;

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
            // ⭐ MODIFICADO: Leer emails con opción de marcar como leído
            // ----------------------------------------------

            try (EmailReaderService.EmailReadContext context =
                         emailReaderService.openEmailFolder(emailAccount, markAsRead,false)) {

                // Leer emails nuevos
                List<EmailReaderService.EmailMessage> newEmails =
                        readEmailsFromContext(context.getFolder(), emailAccount);

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
                        boolean success = processEmail(emailAccount, emailMessage);

                        if (success) {
                            processedCount++;
                            lastProcessedUid = emailMessage.uid;

                            // ⭐ NUEVO: Marcar como leído SOLO si se procesó exitosamente
                            if (markAsRead) {
                                try {
                                    context.markAsRead(emailMessage);
                                } catch (Exception e) {
                                    log.warn("⚠️  Could not mark email {} as read: {}",
                                            emailMessage.uid, e.getMessage());
                                }
                            }
                        }

                    } catch (Exception e) {
                        log.error("❌ Error processing email {}: {}",
                                emailMessage.uid, e.getMessage(), e);
                    }
                }

                // Actualizar última fecha y UID procesado
                emailAccount.setLastProcessedUid(lastProcessedUid);
                updateAccountPollDates(emailAccount, true);

                log.info("✅ Successfully processed {} emails from {}",
                        processedCount, emailAccount.getEmailAddress());

                return processedCount;

            } // AutoCloseable cierra la conexión aquí

        } catch (Exception e) {
            log.error("❌ Error processing emails from {}: {}",
                    emailAccount.getEmailAddress(), e.getMessage(), e);
            updateAccountPollDates(emailAccount, false);
            return 0;
        }
    }

    /**
     * Leer emails NO LEÍDOS desde un folder
     *
     * ⭐ OPTIMIZADO: Usa folder.search(UNSEEN) en lugar de getMessages()
     * Esto significa que solo lee los emails nuevos, no toda la casilla
     */
    private List<EmailReaderService.EmailMessage> readEmailsFromContext(
            jakarta.mail.Folder folder,
            EmailAccount emailAccount) throws Exception {

        List<EmailReaderService.EmailMessage> emails = new java.util.ArrayList<>();

        // ⭐ OPTIMIZACIÓN: Buscar solo mensajes NO LEÍDOS
        // En lugar de: folder.getMessages() que lee TODOS
        jakarta.mail.Message[] messages;

        try {
            // Crear término de búsqueda: SEEN = false (no leídos)
            FlagTerm unseenTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);

            // Buscar solo mensajes que cumplan el criterio
            messages = folder.search(unseenTerm);

            log.info("📬 Found {} UNREAD messages (using UNSEEN flag)", messages.length);

        } catch (Exception e) {
            // Fallback: Si search() falla, usar getMessages() y filtrar
            log.warn("⚠️  Could not use search(), falling back to getMessages(): {}", e.getMessage());
            messages = folder.getMessages();
            log.info("📬 Found {} total messages (will filter by UID)", messages.length);
        }

        for (jakarta.mail.Message message : messages) {
            try {
                // Obtener UID del mensaje
                String uid = getMessageUid(folder, message);

                // ⭐ NOTA: Ya no necesitamos filtrar por lastProcessedUid
                // porque folder.search(UNSEEN) solo retorna NO LEÍDOS
                // Pero lo dejamos como verificación adicional
                if (emailAccount.getLastProcessedUid() != null &&
                        uid.compareTo(emailAccount.getLastProcessedUid()) <= 0) {
                    log.debug("⏭️  Skipping already processed UID: {}", uid);
                    continue;
                }

                // Parsear mensaje
                EmailReaderService.EmailMessage email = new EmailReaderService.EmailMessage();
                email.rawMessage = message;
                email.uid = uid;
                email.subject = message.getSubject();

                // From
                jakarta.mail.Address[] fromAddrs = message.getFrom();
                if (fromAddrs != null && fromAddrs.length > 0) {
                    email.fromAddresses = jakarta.mail.internet.InternetAddress.toString(fromAddrs);
                    if (fromAddrs[0] instanceof jakarta.mail.internet.InternetAddress) {
                        email.fromAddress = ((jakarta.mail.internet.InternetAddress) fromAddrs[0]).getAddress();
                    } else {
                        email.fromAddress = fromAddrs[0].toString();
                    }
                }

                // ⭐ To, CC, BCC - Parsear correctamente
                email.toAddresses = parseAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.TO));
                email.ccAddresses = parseAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.CC));
                email.bccAddresses = parseAddresses(message.getRecipients(jakarta.mail.Message.RecipientType.BCC));

                // Dates
                java.util.Date sentDate = message.getSentDate();
                if (sentDate != null) {
                    email.sentDate = sentDate.toInstant();
                }

                java.util.Date receivedDate = message.getReceivedDate();
                if (receivedDate != null) {
                    email.receivedDate = receivedDate.toInstant();
                } else {
                    email.receivedDate = java.time.Instant.now();
                }

                // Attachments
                email.attachments = extractAttachmentsSimple(message);

                emails.add(email);

            } catch (Exception e) {
                log.error("❌ Error parsing message", e);
            }
        }

        return emails;
    }

    /**
     * Obtener UID del mensaje (copiado de EmailReaderService)
     */
    private String getMessageUid(jakarta.mail.Folder folder, jakarta.mail.Message message)
            throws jakarta.mail.MessagingException {
        try {
            if (folder instanceof com.sun.mail.imap.IMAPFolder) {
                com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
                long uid = imapFolder.getUID(message);
                return String.valueOf(uid);
            } else {
                String[] messageIds = message.getHeader("Message-ID");
                if (messageIds != null && messageIds.length > 0) {
                    return messageIds[0];
                }
                return String.valueOf(message.getMessageNumber());
            }
        } catch (Exception e) {
            log.warn("Could not get UID, using message number", e);
            return String.valueOf(message.getMessageNumber());
        }
    }

    /**
     * Extraer attachments simplificado
     */
    private List<EmailReaderService.AttachmentInfo> extractAttachmentsSimple(jakarta.mail.Message message)
            throws Exception {
        List<EmailReaderService.AttachmentInfo> attachments = new java.util.ArrayList<>();

        if (message.isMimeType("multipart/*")) {
            jakarta.mail.Multipart multipart = (jakarta.mail.Multipart) message.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                Part part = multipart.getBodyPart(i);
                String disposition = part.getDisposition();
                String filename = part.getFileName();

                if (disposition != null &&
                        (disposition.equalsIgnoreCase(Part.ATTACHMENT) ||
                                disposition.equalsIgnoreCase(Part.INLINE))) {
                    if (filename != null) {
                        attachments.add(new EmailReaderService.AttachmentInfo(
                                filename, part.getContentType(), part));
                    }
                } else if (filename != null) {
                    attachments.add(new EmailReaderService.AttachmentInfo(
                            filename, part.getContentType(), part));
                }
            }
        }

        return attachments;
    }

    /**
     * Parsear addresses (To, CC, BCC) a lista de strings
     */
    private List<String> parseAddresses(jakarta.mail.Address[] addresses) {
        List<String> result = new java.util.ArrayList<>();

        if (addresses == null || addresses.length == 0) {
            return result;
        }

        for (jakarta.mail.Address address : addresses) {
            if (address instanceof jakarta.mail.internet.InternetAddress) {
                jakarta.mail.internet.InternetAddress ia = (jakarta.mail.internet.InternetAddress) address;
                result.add(ia.getAddress());
            } else {
                result.add(address.toString());
            }
        }

        return result;
    }

    /**
     * Procesar un email individual
     *
     * @return true si se procesó exitosamente
     */
    private boolean processEmail(
            EmailAccount emailAccount,
            EmailReaderService.EmailMessage emailMessage) {

        // Generar correlationId único para este email
        String correlationId = UUID.randomUUID().toString();

        // Setear en MDC para que aparezca en todos los logs
        MDC.put("correlationId", correlationId);

        try {
            log.info("📨 [START] Processing email from {}: {} [correlationId={}]",
                    emailMessage.fromAddress, emailMessage.subject, correlationId);

            // 1. Buscar regla de sender
            Optional<EmailSenderRule> senderRuleOpt = senderRuleRepository
                    .findByEmailAccountIdAndSenderEmail(
                            emailAccount.getId(),
                            emailMessage.fromAddress);

            // 2. Crear registro de email procesado
            ProcessedEmail processedEmail = buildProcessedEmail(
                    emailAccount, emailMessage, senderRuleOpt.orElse(null));

            // Setear correlationId en el email
            processedEmail.setCorrelationId(correlationId);

            // 3. Validar si debe procesarse
            if (senderRuleOpt.isEmpty()) {
                log.info("⏭️ No sender rule for {}, marking as IGNORED", emailMessage.fromAddress);
                processedEmail.markAsIgnored();
                processedEmailRepository.save(processedEmail);
                log.info("✓ [END] Email processing finished (IGNORED) [correlationId={}]", correlationId);
                return false; // No se procesó
            }

            EmailSenderRule senderRule = senderRuleOpt.get();

            if (!senderRule.getProcessEnabled()) {
                log.info("⏭️ Processing disabled for {}, marking as IGNORED", emailMessage.fromAddress);
                processedEmail.markAsIgnored();
                processedEmailRepository.save(processedEmail);
                log.info("✓ [END] Email processing finished (DISABLED) [correlationId={}]", correlationId);
                return false; // No se procesó
            }

            // 4. Guardar email en DB
            processedEmail = processedEmailRepository.save(processedEmail);

            try {
                // 5. Procesar attachments
                List<ProcessedAttachment> savedAttachments = processAttachments(
                        processedEmail, senderRule, emailMessage.attachments);

                // 6. Generar metadata JSON directamente con los attachments procesados
                // No necesitamos recargar desde DB, ya tenemos los objetos
                Map<String, Object> metadata = helpers.generateMetadataFromAttachments(
                        processedEmail, savedAttachments);
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
                processedEmail.setProcessedAttachments(savedAttachments.size());
                processedEmail.setMetadataFilePath(metadataPath);
                processedEmail.setRawMetadata("metadata");
                processedEmail.markAsCompleted();
                processedEmailRepository.save(processedEmail);

                log.info("✅ [END] Email {} processed successfully: {} attachments [correlationId={}]",
                        emailMessage.uid, savedAttachments.size(), correlationId);

                return true; // Procesado exitosamente

            } catch (Exception e) {
                log.error("❌ [ERROR] Error processing email: {} [correlationId={}]", e.getMessage(), correlationId, e);
                processedEmail.markAsFailed(e.getMessage());
                processedEmailRepository.save(processedEmail);
                return false; // Falló el procesamiento
            }

        } finally {
            // Limpiar correlationId del MDC
            MDC.remove("correlationId");
        }
    }

    /**
     * Procesar attachments del email
     *
     * @return Lista de attachments procesados exitosamente
     */
    private List<ProcessedAttachment> processAttachments(
            ProcessedEmail processedEmail,
            EmailSenderRule senderRule,
            List<EmailReaderService.AttachmentInfo> attachments) {

        List<ProcessedAttachment> savedAttachments = new java.util.ArrayList<>();
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
                    // No agregar a savedAttachments (fue ignorado)
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
                    attachment = processedAttachmentRepository.save(attachment);

                    // Agregar a lista de procesados exitosamente
                    savedAttachments.add(attachment);
                    sequence++;

                    log.info("✅ Saved: {} → {}", attachmentInfo.filename, normalizedFilename);

                } catch (Exception e) {
                    log.error("❌ Error saving {}: {}", attachmentInfo.filename, e.getMessage());
                    attachment.markAsFailed(e.getMessage());
                    processedAttachmentRepository.save(attachment);
                    // No agregar a savedAttachments (falló)
                }

            } catch (Exception e) {
                log.error("❌ Error processing attachment: {}", e.getMessage(), e);
            }
        }

        return savedAttachments;
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

        // Obtener storageType del tenant, con fallback a LOCAL
        StorageType storageType = StorageType.LOCAL; // Default

        try {
            if (processedEmail.getTenant() != null &&
                    processedEmail.getTenant().getStorageType() != null) {
                storageType = processedEmail.getTenant().getStorageType();
            }
        } catch (Exception e) {
            log.warn("Could not get storage type from tenant, using LOCAL", e);
        }

        return ProcessedAttachment.builder()
                .processedEmail(processedEmail)
                .rule(rule)
                .originalFilename(attachmentInfo.filename)
                .mimeType(attachmentInfo.mimeType)
                .processingStatus(AttachmentProcessingStatus.PENDING)
                .storageType(storageType)
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
