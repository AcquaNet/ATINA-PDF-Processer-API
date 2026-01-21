package com.atina.invoice.api.scheduler;

import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.repository.EmailAccountRepository;
import com.atina.invoice.api.service.EmailProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler para polling automático de emails
 *
 * Se ejecuta cada minuto y verifica qué cuentas necesitan ser procesadas
 * según su pollingIntervalMinutes configurado.
 *
 * Para habilitar/deshabilitar:
 * application.properties: email.polling.enabled=true/false
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "email.polling.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EmailPollingScheduler {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailProcessingService emailProcessingService;

    /**
     * Ejecutar cada minuto
     *
     * Verifica todas las cuentas habilitadas para polling y procesa
     * aquellas que ya superaron su intervalo de polling.
     */
    @Scheduled(fixedRate = 60000) // 60 segundos = 1 minuto
    public void pollEmails() {

        log.info("BEGIN START POLL EMAILS: Running email polling scheduler...");

        try {

            // --------------------------------------------------------
            // 1. Obtener todas las cuentas habilitadas para polling
            // --------------------------------------------------------

            List<EmailAccount> activeAccounts = emailAccountRepository
                    .findAllWithPollingEnabled();

            if (activeAccounts.isEmpty()) {
                log.info("START POLL EMAILS: No email accounts enabled for polling");
                return;
            }

            log.info("START POLL EMAILS:Found {} email accounts enabled for polling", activeAccounts.size());

            // 2. Procesar cada cuenta que necesite polling
            int accountsProcessed = 0;
            int totalEmailsProcessed = 0;

            for (EmailAccount account : activeAccounts) {
                try {
                    // Verificar si ya es hora de hacer polling
                    if (shouldPoll(account)) {
                        log.info("START POLL EMAILS: Polling emails from: {}", account.getEmailAddress());

                        // Procesar emails de esta cuenta
                        int emailsProcessed = emailProcessingService
                                .processEmailsFromAccount(account);

                        accountsProcessed++;
                        totalEmailsProcessed += emailsProcessed;

                        if (emailsProcessed > 0) {
                            log.info("START POLL EMAILS: Processed {} emails from {}",
                                    emailsProcessed, account.getEmailAddress());
                        }
                    } else {
                        log.info("START POLL EMAILS: Skipping {} - next poll in {} minutes",
                                account.getEmailAddress(),
                                getMinutesUntilNextPoll(account));
                    }

                } catch (Exception e) {
                    log.info("START POLL EMAILS: Error polling account {}: {}",
                            account.getEmailAddress(), e.getMessage(), e);
                }
            }

            if (accountsProcessed > 0) {
                log.info("START POLL EMAILS: Polling completed: {} accounts processed, {} emails total",
                        accountsProcessed, totalEmailsProcessed);
            }

        } catch (Exception e) {
            log.error("START POLL EMAILS: Error in email polling scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Verificar si una cuenta debe ser polleada ahora
     *
     * Una cuenta debe ser polleada si:
     * 1. Nunca ha sido polleada (lastPollDate == null)
     * 2. Ha pasado el tiempo suficiente desde el último poll
     *
     * @param account Cuenta de email
     * @return true si debe ser polleada
     */
    private boolean shouldPoll(EmailAccount account) {
        // Si nunca fue polleada, hacerlo ahora
        if (account.getLastPollDate() == null) {
            return true;
        }

        // Calcular tiempo desde último poll
        Instant now = Instant.now();
        long minutesSinceLastPoll = ChronoUnit.MINUTES.between(
                account.getLastPollDate(), now);

        // Comparar con el intervalo configurado
        Integer pollingInterval = account.getPollingIntervalMinutes();
        if (pollingInterval == null) {
            pollingInterval = 10; // Default: 10 minutos
        }

        return minutesSinceLastPoll >= pollingInterval;
    }

    /**
     * Obtener minutos hasta el próximo poll
     * Útil para logging
     */
    private long getMinutesUntilNextPoll(EmailAccount account) {
        if (account.getLastPollDate() == null) {
            return 0;
        }

        Instant now = Instant.now();
        long minutesSinceLastPoll = ChronoUnit.MINUTES.between(
                account.getLastPollDate(), now);

        Integer pollingInterval = account.getPollingIntervalMinutes();
        if (pollingInterval == null) {
            pollingInterval = 10;
        }

        long minutesUntilNext = pollingInterval - minutesSinceLastPoll;
        return Math.max(0, minutesUntilNext);
    }

    /**
     * Ejecutar polling manual para una cuenta específica
     * Útil para testing o forzar un poll
     *
     * @param emailAccountId ID de la cuenta
     * @return Número de emails procesados
     */
    public int pollAccountNow(Long emailAccountId) {
        log.info("🔄 Manual polling triggered for account ID: {}", emailAccountId);

        EmailAccount account = emailAccountRepository.findById(emailAccountId)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + emailAccountId));

        if (!account.getPollingEnabled()) {
            log.warn("⚠️ Account {} is not enabled for polling", account.getEmailAddress());
            throw new RuntimeException("Account is not enabled for polling");
        }

        int emailsProcessed = emailProcessingService.processEmailsFromAccount(account);

        log.info("✅ Manual polling completed: {} emails processed from {}",
                emailsProcessed, account.getEmailAddress());

        return emailsProcessed;
    }
}
