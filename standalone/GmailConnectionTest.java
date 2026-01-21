import jakarta.mail.*;
import java.util.Properties;

/**
 * Test de conexión Gmail con configuración EXACTA de Mulesoft
 *
 * Compilar y ejecutar:
 * javac -cp ".:jakarta.mail-api-2.1.0.jar:jakarta.mail-2.0.1.jar" GmailConnectionTestMulesoft.java
 * java -cp ".:jakarta.mail-api-2.1.0.jar:jakarta.mail-2.0.1.jar" GmailConnectionTestMulesoft
 */
public class GmailConnectionTest {

    public static void main(String[] args) {
        // ========== CONFIGURACIÓN EXACTA DE MULESOFT ==========
        String host = "imap.gmail.com";
        int port = 993;
        String username = "soportecompanyintegracion@gmail.com";
        String password = "seanqimxvltgpcrx";  // App Password

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Gmail Connection Test (Mulesoft Configuration)         ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("📋 Configuration:");
        System.out.println("   Host: " + host);
        System.out.println("   Port: " + port);
        System.out.println("   User: " + username);
        System.out.println("   Pass: " + maskPassword(password));
        System.out.println();

        // ========== CREAR PROPERTIES COMO EN MULESOFT ==========
        Properties props = new Properties();

        // Protocol & Host
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));

        // SSL/TLS (TLSv1.2 como en Mulesoft)
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.ssl.protocols", "TLSv1.2");
        props.put("mail.imap.ssl.trust", host);
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
        props.put("mail.imap.socketFactory.port", String.valueOf(port));

        // Debug (opcional)
        props.put("mail.debug", "false");

        System.out.println("🔧 JavaMail Properties:");
        System.out.println("   SSL: enabled");
        System.out.println("   Protocols: TLSv1.2");
        System.out.println("   Auth: LOGIN PLAIN");
        System.out.println("   Timeouts: 10s");
        System.out.println();

        // ========== CONECTAR ==========
        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;

        try {
            System.out.println("🔌 Connecting to Gmail...");

            store = session.getStore("imap");
            store.connect(host, port, username, password);

            System.out.println("✅ Connection successful!");
            System.out.println();

            // Abrir INBOX
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int messageCount = inbox.getMessageCount();
            int unreadCount = inbox.getUnreadMessageCount();

            System.out.println("📧 INBOX Status:");
            System.out.println("   Total messages: " + messageCount);
            System.out.println("   Unread messages: " + unreadCount);
            System.out.println();

            // Mostrar algunos mensajes
            if (messageCount > 0) {
                System.out.println("📬 Latest messages:");
                Message[] messages = inbox.getMessages(
                    Math.max(1, messageCount - 4),
                    messageCount
                );

                for (int i = 0; i < messages.length && i < 5; i++) {
                    Message msg = messages[i];
                    System.out.printf("   %d. From: %s%n",
                        (i + 1),
                        msg.getFrom()[0].toString()
                    );
                    System.out.printf("      Subject: %s%n",
                        msg.getSubject()
                    );
                    System.out.printf("      Date: %s%n",
                        msg.getReceivedDate()
                    );
                    System.out.println();
                }
            }

            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║  ✅ TEST PASSED - Configuration is CORRECT              ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

        } catch (AuthenticationFailedException e) {
            System.err.println("╔══════════════════════════════════════════════════════════╗");
            System.err.println("║  ❌ AUTHENTICATION FAILED                                ║");
            System.err.println("╚══════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("❌ Error: " + e.getMessage());
            System.err.println();
            System.err.println("🔍 Possible causes:");
            System.err.println("   1. App Password is incorrect or expired");
            System.err.println("   2. 2FA is not enabled on Gmail account");
            System.err.println("   3. IMAP is disabled in Gmail settings");
            System.err.println();
            System.err.println("📋 How to fix:");
            System.err.println("   1. Go to: https://myaccount.google.com/apppasswords");
            System.err.println("   2. Revoke old App Password");
            System.err.println("   3. Generate new App Password");
            System.err.println("   4. Use the new 16-char password (no spaces)");
            System.err.println();

            e.printStackTrace();
            System.exit(1);

        } catch (MessagingException e) {
            System.err.println("╔══════════════════════════════════════════════════════════╗");
            System.err.println("║  ❌ CONNECTION ERROR                                     ║");
            System.err.println("╚══════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("❌ Error: " + e.getMessage());
            System.err.println();
            System.err.println("🔍 Possible causes:");
            System.err.println("   1. Network connectivity issues");
            System.err.println("   2. Firewall blocking port 993");
            System.err.println("   3. SSL/TLS handshake failure");
            System.err.println();

            e.printStackTrace();
            System.exit(1);

        } catch (Exception e) {
            System.err.println("❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);

        } finally {
            // Cerrar conexiones
            try {
                if (inbox != null && inbox.isOpen()) {
                    inbox.close(false);
                }
                if (store != null && store.isConnected()) {
                    store.close();
                    System.out.println("🔌 Connection closed");
                }
            } catch (Exception e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Enmascarar password para logs
     */
    private static String maskPassword(String password) {
        if (password == null || password.length() < 4) {
            return "****";
        }
        return password.substring(0, 2) + "****" +
               password.substring(password.length() - 2);
    }
}
