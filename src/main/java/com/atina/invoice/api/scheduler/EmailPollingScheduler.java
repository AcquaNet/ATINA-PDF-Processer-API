package com.atina.invoice.api.scheduler;

import com.atina.invoice.api.service.EmailPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler para polling automático de emails
 *
 * Se ejecuta cada minuto y verifica qué cuentas necesitan ser procesadas
 * según su pollingIntervalMinutes configurado.
 *
 * Para habilitar/deshabilitar:
 * application.properties: email.polling.enabled=true/false
 *
 * MODIFICADO: Ahora usa EmailPollingService compartido
 * Esto permite que el controller funcione independientemente del scheduler
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

    /**
     * ⭐ CAMBIO: Ahora usa EmailPollingService en lugar de EmailProcessingService
     *
     * ANTES:
     * private final EmailAccountRepository emailAccountRepository;
     * private final EmailProcessingService emailProcessingService;
     *
     * DESPUÉS:
     * private final EmailPollingService pollingService;
     *
     * VENTAJA: El servicio está disponible para el controller incluso si
     * el scheduler está deshabilitado (email.polling.enabled=false)
     */
    private final EmailPollingService pollingService;

    /**
     * Ejecutar cada minuto
     *
     * Verifica todas las cuentas habilitadas para polling y procesa
     * aquellas que ya superaron su intervalo de polling.
     */
    @Scheduled(fixedRate = 60000) // 60 segundos = 1 minuto
    public void pollEmails() {

        log.debug("BEGIN EMAIL POLLING: 🔄 Running email polling scheduler...");

        try {


            // -----------------------------------------------
            // Invocar el servicio compartido para polling
            // -----------------------------------------------

             int totalEmailsProcessed = pollingService.pollAllAccounts();

            if (totalEmailsProcessed > 0) {
                log.info("END EMAIL POLLING: ✅ Scheduler: Processed {} emails", totalEmailsProcessed);
            }

        } catch (Exception e) {
            log.error("END EMAIL POLLING: ❌ Error in email polling scheduler: {}", e.getMessage(), e);
        }
    }
}
