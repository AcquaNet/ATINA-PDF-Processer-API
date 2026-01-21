package com.atina.invoice.api.service;

import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.model.enums.EmailType;
import com.atina.invoice.api.security.AesGcmCrypto;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * Servicio para leer emails desde IMAP/POP3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailReaderService {

    private final AesGcmCrypto aesGcmCrypto;

    /**
     * DTO para representar un email leído
     */
    public static class EmailMessage {
        public String uid;
        public String subject;
        public String fromAddress;
        public String fromAddresses;
        public List<String> toAddresses;
        public List<String> ccAddresses;
        public List<String> bccAddresses;
        public Instant sentDate;
        public Instant receivedDate;
        public List<AttachmentInfo> attachments;

        public EmailMessage() {
            this.toAddresses = new ArrayList<>();
            this.ccAddresses = new ArrayList<>();
            this.bccAddresses = new ArrayList<>();
            this.attachments = new ArrayList<>();
        }
    }

    /**
     * DTO para representar un attachment
     */
    public static class AttachmentInfo {
        public String filename;
        public String mimeType;
        public Part part;

        public AttachmentInfo(String filename, String mimeType, Part part) {
            this.filename = filename;
            this.mimeType = mimeType;
            this.part = part;
        }
    }

    /**
     * Leer emails nuevos de una cuenta
     * 
     * @param emailAccount Cuenta de email configurada
     * @return Lista de emails nuevos
     */
    public List<EmailMessage> readNewEmails(EmailAccount emailAccount) {
        List<EmailMessage> emails = new ArrayList<>();

        try {

            // ------------------------------------------
            // Configurar propiedades según el tipo
            // ------------------------------------------

            Properties props = new Properties();
            Session session;
            Store store;
            String rawPassword = "";

            // ------------------------------------------
            // Segun el tipo de cuenta
            // ------------------------------------------

            if (emailAccount.getEmailType() == EmailType.IMAP) {

                // ------------------------------------------
                // IMAP
                // ------------------------------------------

                rawPassword = aesGcmCrypto.decryptFromBase64(emailAccount.getPassword());

                // Protocol & Host
                props.put("mail.store.protocol", "imap");
                props.put("mail.imap.host", emailAccount.getHost());
                props.put("mail.imap.port", String.valueOf(emailAccount.getPort()));

                // SSL/TLS (TLSv1.2 como en Mulesoft)
                props.put("mail.imap.ssl.enable", "true");
                props.put("mail.imap.ssl.protocols", "TLSv1.2");
                props.put("mail.imap.ssl.trust", emailAccount.getHost());
                props.put("mail.imap.ssl.checkserveridentity", "true");

                // Authentication
                props.put("mail.imap.auth", "true");
                props.put("mail.imap.auth.mechanisms", "LOGIN PLAIN");

                // Timeouts (10 segundos como en Mulesoft)
                props.put("mail.imap.connectiontimeout", "10000");
                props.put("mail.imap.timeout", "10000");
                props.put("mail.imap.writetimeout", "10000");

                // SSL Socket Factory
                props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.imap.socketFactory.fallback", "false");
                props.put("mail.imap.socketFactory.port", String.valueOf(emailAccount.getPort()));

                // Debug (opcional)
                props.put("mail.debug", "false");

                session = Session.getInstance(props);

                store = session.getStore("imap");


            } else {

                // ------------------------------------------
                // POP3
                // ------------------------------------------

                props.put("mail.store.protocol", "pop3");
                props.put("mail.pop3.host", emailAccount.getHost());
                props.put("mail.pop3.port", emailAccount.getPort());
                if (emailAccount.getUseSsl()) {
                    props.put("mail.pop3.ssl.enable", "true");
                }
                props.put("mail.pop3.connectiontimeout", "30000");
                props.put("mail.pop3.timeout", "30000");

                session = Session.getInstance(props);

                store = session.getStore("pop3");

            }

            // ------------------------------------------
            // Conectar
            // ------------------------------------------

            store.connect( emailAccount.getHost(), emailAccount.getPort(), emailAccount.getUsername(), rawPassword);

            log.info("Connected to {} account: {}", emailAccount.getEmailType(),
                    emailAccount.getEmailAddress());

            // ------------------------------------------
            // Abrir carpeta
            // ------------------------------------------

            Folder folder = store.getFolder(emailAccount.getFolderName());
            folder.open(Folder.READ_ONLY);
            log.info("Opened folder: {} with {} messages", 
                    emailAccount.getFolderName(), folder.getMessageCount());

            // ------------------------------------------
            // Obtener mensajes
            // ------------------------------------------
            Message[] messages = folder.getMessages();

            // ------------------------------------------
            // Procesar cada mensaje
            // ------------------------------------------

            for (Message message : messages) {
                try {
                    // Obtener UID del mensaje
                    String uid = getMessageUid(folder, message);

                    // Si ya fue procesado, skip
                    if (emailAccount.getLastProcessedUid() != null && 
                        uid.compareTo(emailAccount.getLastProcessedUid()) <= 0) {
                        continue;
                    }

                    // Parsear mensaje
                    EmailMessage email = parseMessage(message, uid);
                    emails.add(email);

                } catch (Exception e) {
                    log.error("Error parsing message", e);
                }
            }

            // Cerrar carpeta y store
            folder.close(false);
            store.close();

            log.info("Read {} new emails from {}", emails.size(), emailAccount.getEmailAddress());

        } catch (MessagingException e) {
            log.error("Error reading emails from {}: {}", 
                    emailAccount.getEmailAddress(), e.getMessage(), e);
        }

        return emails;
    }

    /**
     * Obtener UID del mensaje
     */
    private String getMessageUid(Folder folder, Message message) throws MessagingException {
        try {
            // Para IMAP, usar UIDFolder
            if (folder instanceof com.sun.mail.imap.IMAPFolder) {
                com.sun.mail.imap.IMAPFolder imapFolder = (com.sun.mail.imap.IMAPFolder) folder;
                long uid = imapFolder.getUID(message);
                return String.valueOf(uid);
            } else {
                // Para POP3, usar Message-ID header
                String[] messageIds = message.getHeader("Message-ID");
                if (messageIds != null && messageIds.length > 0) {
                    return messageIds[0];
                }
                // Fallback: usar número de mensaje
                return String.valueOf(message.getMessageNumber());
            }
        } catch (Exception e) {
            log.warn("Could not get UID, using message number", e);
            return String.valueOf(message.getMessageNumber());
        }
    }

    /**
     * Parsear un mensaje de email
     */
    private EmailMessage parseMessage(Message message, String uid) throws Exception {
        EmailMessage email = new EmailMessage();

        // UID
        email.uid = uid;

        // Subject
        email.subject = message.getSubject();

        // From
        Address[] fromAddrs = message.getFrom();
        if (fromAddrs != null && fromAddrs.length > 0) {
            email.fromAddresses = InternetAddress.toString(fromAddrs);
            if (fromAddrs[0] instanceof InternetAddress) {
                email.fromAddress = ((InternetAddress) fromAddrs[0]).getAddress();
            } else {
                email.fromAddress = fromAddrs[0].toString();
            }
        }

        // To
        Address[] toAddrs = message.getRecipients(Message.RecipientType.TO);
        if (toAddrs != null) {
            for (Address addr : toAddrs) {
                if (addr instanceof InternetAddress) {
                    email.toAddresses.add(((InternetAddress) addr).getAddress());
                } else {
                    email.toAddresses.add(addr.toString());
                }
            }
        }

        // CC
        Address[] ccAddrs = message.getRecipients(Message.RecipientType.CC);
        if (ccAddrs != null) {
            for (Address addr : ccAddrs) {
                if (addr instanceof InternetAddress) {
                    email.ccAddresses.add(((InternetAddress) addr).getAddress());
                } else {
                    email.ccAddresses.add(addr.toString());
                }
            }
        }

        // BCC
        Address[] bccAddrs = message.getRecipients(Message.RecipientType.BCC);
        if (bccAddrs != null) {
            for (Address addr : bccAddrs) {
                if (addr instanceof InternetAddress) {
                    email.bccAddresses.add(((InternetAddress) addr).getAddress());
                } else {
                    email.bccAddresses.add(addr.toString());
                }
            }
        }

        // Dates
        Date sentDate = message.getSentDate();
        if (sentDate != null) {
            email.sentDate = sentDate.toInstant();
        }

        Date receivedDate = message.getReceivedDate();
        if (receivedDate != null) {
            email.receivedDate = receivedDate.toInstant();
        } else {
            email.receivedDate = Instant.now();
        }

        // Attachments
        email.attachments = extractAttachments(message);

        return email;
    }

    /**
     * Extraer attachments de un mensaje
     */
    private List<AttachmentInfo> extractAttachments(Message message) throws Exception {
        List<AttachmentInfo> attachments = new ArrayList<>();

        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                Part part = multipart.getBodyPart(i);

                String disposition = part.getDisposition();
                String filename = part.getFileName();

                // Es un attachment si tiene disposition ATTACHMENT o tiene filename
                if (disposition != null && 
                    (disposition.equalsIgnoreCase(Part.ATTACHMENT) || 
                     disposition.equalsIgnoreCase(Part.INLINE))) {
                    
                    if (filename != null) {
                        attachments.add(new AttachmentInfo(
                            filename, 
                            part.getContentType(), 
                            part
                        ));
                    }
                } else if (filename != null) {
                    // Algunos servidores no ponen disposition pero sí filename
                    attachments.add(new AttachmentInfo(
                        filename, 
                        part.getContentType(), 
                        part
                    ));
                }
            }
        }

        return attachments;
    }
}
