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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

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
        public Message rawMessage; // ⭐ NUEVO: Referencia al mensaje original para marcar como leído

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
     * Contexto de lectura de emails
     * Contiene la conexión abierta para poder marcar emails como leídos
     */
    public static class EmailReadContext implements AutoCloseable {
        private final Store store;
        private final Folder folder;
        private final boolean readWrite;

        public EmailReadContext(Store store, Folder folder, boolean readWrite) {
            this.store = store;
            this.folder = folder;
            this.readWrite = readWrite;
        }

        /**
         * Obtener folder
         */
        public Folder getFolder() {
            return folder;
        }

        /**
         * Obtener store
         */
        public Store getStore() {
            return store;
        }

        /**
         * Verificar si está en modo READ_WRITE
         */
        public boolean isReadWrite() {
            return readWrite;
        }

        /**
         * Marcar un email como leído
         */
        public void markAsRead(EmailMessage email) throws MessagingException {
            if (!readWrite) {
                throw new IllegalStateException("Cannot mark as read: folder opened in READ_ONLY mode");
            }
            if (email.rawMessage != null) {
                email.rawMessage.setFlag(Flags.Flag.SEEN, true);
                log.debug("✅ Marked email as read: {}", email.uid);
            }
        }

        @Override
        public void close() throws Exception {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    /**
     * Leer emails nuevos de una cuenta
     *
     * @param emailAccount Cuenta de email configurada
     * @param markAsRead Si debe marcar los emails como leídos después de procesarlos
     * @return Lista de emails nuevos
     */
    public List<EmailMessage> readNewEmails(EmailAccount emailAccount, boolean markAsRead) {
        List<EmailMessage> emails = new ArrayList<>();

        try (EmailReadContext context = openEmailFolder(emailAccount, markAsRead)) {

            // Obtener mensajes
            Message[] messages = context.folder.getMessages();

            // Procesar cada mensaje
            for (Message message : messages) {
                try {
                    // Obtener UID del mensaje
                    String uid = getMessageUid(context.folder, message);

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

            log.info("Read {} new emails from {}", emails.size(), emailAccount.getEmailAddress());

        } catch (Exception e) {
            log.error("Error reading emails from {}: {}",
                    emailAccount.getEmailAddress(), e.getMessage(), e);
        }

        return emails;
    }

    /**
     * Leer emails nuevos (versión anterior sin marcar como leído)
     * Mantiene compatibilidad con código existente
     */
    public List<EmailMessage> readNewEmails(EmailAccount emailAccount) {
        return readNewEmails(emailAccount, false);
    }

    /**
     * Abrir folder de emails
     *
     * @param emailAccount Cuenta de email
     * @param readWrite Si debe abrir en modo READ_WRITE (para marcar como leído)
     * @return Contexto de lectura
     */
    public EmailReadContext openEmailFolder(EmailAccount emailAccount, boolean readWrite)
            throws MessagingException {

        Properties props = new Properties();
        Session session;
        Store store;
        String rawPassword = aesGcmCrypto.decryptFromBase64(emailAccount.getPassword());

        if (emailAccount.getEmailType() == EmailType.IMAP) {
            // Configuración IMAP
            props.put("mail.store.protocol", "imap");
            props.put("mail.imap.host", emailAccount.getHost());
            props.put("mail.imap.port", String.valueOf(emailAccount.getPort()));
            props.put("mail.imap.ssl.enable", "true");
            props.put("mail.imap.ssl.protocols", "TLSv1.2");
            props.put("mail.imap.ssl.trust", emailAccount.getHost());
            props.put("mail.imap.ssl.checkserveridentity", "true");
            props.put("mail.imap.auth", "true");
            props.put("mail.imap.auth.mechanisms", "LOGIN PLAIN");
            props.put("mail.imap.connectiontimeout", "10000");
            props.put("mail.imap.timeout", "10000");
            props.put("mail.imap.writetimeout", "10000");
            props.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.imap.socketFactory.fallback", "false");
            props.put("mail.imap.socketFactory.port", String.valueOf(emailAccount.getPort()));
            props.put("mail.debug", "false");

            session = Session.getInstance(props);
            store = session.getStore("imap");

        } else {
            // Configuración POP3
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

        // Conectar
        store.connect(emailAccount.getHost(), emailAccount.getPort(),
                emailAccount.getUsername(), rawPassword);

        log.info("Connected to {} account: {}", emailAccount.getEmailType(),
                emailAccount.getEmailAddress());

        // Abrir carpeta en el modo apropiado
        Folder folder = store.getFolder(emailAccount.getFolderName());
        int mode = readWrite ? Folder.READ_WRITE : Folder.READ_ONLY;
        folder.open(mode);

        log.info("Opened folder: {} in {} mode with {} messages",
                emailAccount.getFolderName(),
                readWrite ? "READ_WRITE" : "READ_ONLY",
                folder.getMessageCount());

        return new EmailReadContext(store, folder, readWrite);
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

        // ⭐ NUEVO: Guardar referencia al mensaje original
        email.rawMessage = message;

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
