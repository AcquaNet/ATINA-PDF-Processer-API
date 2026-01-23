package com.atina.invoice.api.controller;

import com.atina.invoice.api.service.EmailPollingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para polling manual de emails
 *
 * Permite hacer polling de emails manualmente via API, independientemente
 * del estado del scheduler (email.polling.enabled).
 *
 * IMPORTANTE: Este controller usa EmailPollingService que está SIEMPRE disponible,
 * incluso cuando el scheduler está deshabilitado.
 *
 * Endpoints:
 * - POST /api/email-polling/poll-all - Hacer polling de todas las cuentas
 * - POST /api/email-polling/poll/{accountId} - Hacer polling de una cuenta específica
 */
@Slf4j
@RestController
@RequestMapping("/api/email-polling")
@RequiredArgsConstructor
public class EmailPollingController {

    /**
     * ⭐ IMPORTANTE: Usa EmailPollingService, NO EmailPollingScheduler
     *
     * Esto permite que el controller funcione incluso cuando:
     * - email.polling.enabled=false (scheduler deshabilitado)
     * - El scheduler está pausado o no está corriendo
     *
     * EmailPollingService es un @Service normal que siempre está disponible
     */
    private final EmailPollingService pollingService;

    /**
     * Hacer polling de todas las cuentas que lo necesitan
     *
     * POST /api/email-polling/poll-all
     *
     * Respuesta:
     * {
     *   "success": true,
     *   "emailsProcessed": 15,
     *   "message": "Processed 15 emails from active accounts"
     * }
     */
    @PostMapping("/poll-all")
    public ResponseEntity<PollResponse> pollAllAccounts() {
        log.info("📨 Manual polling triggered for all accounts");

        try {
            int emailsProcessed = pollingService.pollAllAccounts();

            PollResponse response = new PollResponse();
            response.setSuccess(true);
            response.setEmailsProcessed(emailsProcessed);
            response.setMessage(String.format("Processed %d emails from active accounts", emailsProcessed));

            log.info("✅ Manual polling completed: {} emails", emailsProcessed);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error in manual polling: {}", e.getMessage(), e);

            PollResponse response = new PollResponse();
            response.setSuccess(false);
            response.setEmailsProcessed(0);
            response.setMessage("Error: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Hacer polling de una cuenta específica
     *
     * POST /api/email-polling/poll/{accountId}
     *
     * @param accountId ID de la cuenta de email
     *
     * Respuesta:
     * {
     *   "success": true,
     *   "emailsProcessed": 5,
     *   "message": "Processed 5 emails from account"
     * }
     */
    @PostMapping("/poll/{accountId}")
    public ResponseEntity<PollResponse> pollAccount(@PathVariable Long accountId) {
        log.info("📨 Manual polling triggered for account ID: {}", accountId);

        try {
            int emailsProcessed = pollingService.pollAccountNow(accountId);

            PollResponse response = new PollResponse();
            response.setSuccess(true);
            response.setEmailsProcessed(emailsProcessed);
            response.setMessage(String.format("Processed %d emails from account", emailsProcessed));

            log.info("✅ Manual polling completed: {} emails from account {}",
                    emailsProcessed, accountId);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("❌ Error in manual polling: {}", e.getMessage());

            PollResponse response = new PollResponse();
            response.setSuccess(false);
            response.setEmailsProcessed(0);
            response.setMessage("Error: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            log.error("❌ Unexpected error in manual polling: {}", e.getMessage(), e);

            PollResponse response = new PollResponse();
            response.setSuccess(false);
            response.setEmailsProcessed(0);
            response.setMessage("Error: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Response DTO
     */
    @Data
    public static class PollResponse {
        private boolean success;
        private int emailsProcessed;
        private String message;
    }
}
