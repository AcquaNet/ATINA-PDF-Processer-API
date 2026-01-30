package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.model.*;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.atina.invoice.api.repository.ExtractionTaskRepository;
import com.atina.invoice.api.repository.ProcessedEmailRepository;
import com.atina.invoice.api.repository.WebhookCallbackResponseRepository;
import com.atina.invoice.api.service.NotificationDispatcher;
import com.atina.invoice.api.service.notification.NotificationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhook-callback")
@RequiredArgsConstructor
@Tag(name = "Webhook Callback", description = "External webhook callback endpoint")
@SecurityRequirement(name = "bearer-jwt")
public class WebhookCallbackController {

    private final ProcessedEmailRepository processedEmailRepository;
    private final ExtractionTaskRepository extractionTaskRepository;
    private final WebhookCallbackResponseRepository callbackResponseRepository;
    private final NotificationDispatcher notificationDispatcher;

    @PostMapping
    @Operation(
            summary = "Receive webhook callback",
            description = "Receive an external webhook callback and dispatch notifications"
    )
    public ResponseEntity<?> receiveCallback(@RequestBody Map<String, Object> body) {

        long start = System.currentTimeMillis();

        String correlationId = (String) body.get("correlation_id");
        String status = (String) body.get("status");
        String reference = (String) body.get("reference");
        String message = (String) body.get("message");

        if (correlationId == null || correlationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "correlation_id is required"));
        }

        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "status is required"));
        }

        log.info("Received webhook callback: correlationId={}, status={}", correlationId, status);

        // Find the ProcessedEmail by correlationId
        ProcessedEmail email = processedEmailRepository.findByCorrelationId(correlationId)
                .orElse(null);

        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        // Reload with relations (tenant, senderRule)
        email = processedEmailRepository.findByIdWithRelations(email.getId())
                .orElse(null);

        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        // Save callback response
        WebhookCallbackResponse callbackResponse = WebhookCallbackResponse.builder()
                .tenant(email.getTenant())
                .correlationId(correlationId)
                .status(status)
                .reference(reference)
                .message(message)
                .build();

        callbackResponse = callbackResponseRepository.save(callbackResponse);

        log.info("Webhook callback saved: id={}, correlationId={}", callbackResponse.getId(), correlationId);

        // Load extraction tasks for the email
        List<ExtractionTask> tasks = extractionTaskRepository
                .findByEmailIdOrderByCreatedAtAsc(email.getId());

        // Dispatch WEBHOOK_CALLBACK notification
        NotificationContext ctx = NotificationContext.builder()
                .email(email)
                .tasks(tasks)
                .callbackResponse(callbackResponse)
                .tenant(email.getTenant())
                .senderRule(email.getSenderRule())
                .build();

        notificationDispatcher.dispatch(NotificationEvent.WEBHOOK_CALLBACK, ctx);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Callback received", "correlation_id", correlationId),
                MDC.get("correlationId"),
                duration
        ));
    }
}
