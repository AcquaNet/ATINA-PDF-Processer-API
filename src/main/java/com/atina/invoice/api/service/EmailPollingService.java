package com.atina.invoice.api.service;

import com.atina.invoice.api.model.EmailAccount;
import com.atina.invoice.api.repository.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio compartido para polling de emails
 *
 * Usado por:
 * - EmailPollingScheduler (polling automático)
 * - EmailPollingController (polling manual vía API)
 *
 * Este servicio coordina el polling sin depender del scheduler,
 * permitiendo que funcione incluso cuando email.polling.enabled=false
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailPollingService {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailProcessingService emailProcessingService;

    /**
     * Hacer polling de todas las cuentas que lo necesitan
     *
     * @return Número total de emails procesados
     */
    public int pollAllAccounts() {
        log.info("🔄 Starting polling of all accounts...");

        try {
            // Obtener cuentas habilitadas para polling
            List<EmailAccount> activeAccounts = emailAccountRepository.findAllWithPollingEnabled();

            if (activeAccounts.isEmpty()) {
                log.info("ℹ️  No email accounts enabled for polling");
                return 0;
            }

            log.info("📧 Found {} email accounts enabled for polling", activeAccounts.size());

            int accountsProcessed = 0;
            int totalEmailsProcessed = 0;

            for (EmailAccount account : activeAccounts) {
                try {
                    // Verificar si ya es hora de hacer polling
                    if (shouldPoll(account)) {
                        log.info("📨 Polling emails from: {}", account.getEmailAddress());

                        // Procesar emails de esta cuenta
                        int emailsProcessed = emailProcessingService.processEmailsFromAccount(account);

                        accountsProcessed++;
                        totalEmailsProcessed += emailsProcessed;

                        if (emailsProcessed > 0) {
                            log.info("✅ Processed {} emails from {}",
                                    emailsProcessed, account.getEmailAddress());
                        }
                    } else {
                        log.debug("⏭️  Skipping {} - next poll in {} minutes",
                                account.getEmailAddress(), getMinutesUntilNextPoll(account));
                    }

                } catch (Exception e) {
                    log.error("❌ Error polling account {}: {}",
                            account.getEmailAddress(), e.getMessage(), e);
                }
            }

            if (accountsProcessed > 0) {
                log.info("✅ Polling completed: {} accounts processed, {} emails total",
                        accountsProcessed, totalEmailsProcessed);
            }

            return totalEmailsProcessed;

        } catch (Exception e) {
            log.error("❌ Error in polling all accounts: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Hacer polling de una cuenta específica (polling manual)
     *
     * @param emailAccountId ID de la cuenta
     * @return Número de emails procesados
     */
    public int pollAccountNow(Long emailAccountId) {
        log.info("🔄 Manual polling triggered for account ID: {}", emailAccountId);

        EmailAccount account = emailAccountRepository.findById(emailAccountId)
                .orElseThrow(() -> new RuntimeException("Email account not found: " + emailAccountId));

        if (!account.getPollingEnabled()) {
            log.warn("⚠️  Account {} is not enabled for polling", account.getEmailAddress());
            throw new RuntimeException("Account is not enabled for polling");
        }

        int emailsProcessed = emailProcessingService.processEmailsFromAccount(account);

        log.info("✅ Manual polling completed: {} emails processed from {}",
                emailsProcessed, account.getEmailAddress());

        return emailsProcessed;
    }

    /**
     * Verificar si una cuenta debe ser polleada ahora
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
}
