package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.model.ProcessedEmail;
import com.atina.invoice.api.repository.ProcessedEmailRepository;
import com.atina.invoice.api.scheduler.EmailPollingScheduler;
import com.atina.invoice.api.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para gestión de polling de emails
 */
@RestController
@RequestMapping("/api/v1/email-polling")
@RequiredArgsConstructor
@Tag(name = "Email Polling", description = "Email polling and processed emails management")
@SecurityRequirement(name = "bearer-jwt")
public class EmailPollingController {

    private final EmailPollingScheduler pollingScheduler;
    private final ProcessedEmailRepository processedEmailRepository;

    @PostMapping("/poll-now/{emailAccountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Poll emails now",
        description = "Trigger manual polling for a specific email account"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> pollNow(
            @Parameter(description = "Email account ID") @PathVariable Long emailAccountId) {

        int emailsProcessed = pollingScheduler.pollAccountNow(emailAccountId);

        Map<String, Object> result = new HashMap<>();
        result.put("email_account_id", emailAccountId);
        result.put("emails_processed", emailsProcessed);
        result.put("message", emailsProcessed > 0 
                ? "Successfully processed " + emailsProcessed + " emails"
                : "No new emails found");

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/processed-emails")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] List processed emails",
        description = "Get paginated list of processed emails for the current tenant"
    )
    public ResponseEntity<ApiResponse<Page<ProcessedEmail>>> getProcessedEmails(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "processedDate") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "DESC") String sortDir) {

        Long tenantId = TenantContext.getTenantId();

        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProcessedEmail> emails = processedEmailRepository.findByTenantId(tenantId, pageable);

        return ResponseEntity.ok(ApiResponse.success(emails));
    }

    @GetMapping("/processed-emails/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Get processed email",
        description = "Get details of a specific processed email including all attachments"
    )
    public ResponseEntity<ApiResponse<ProcessedEmail>> getProcessedEmail(
            @Parameter(description = "Processed email ID") @PathVariable Long id) {

        Long tenantId = TenantContext.getTenantId();

        ProcessedEmail email = processedEmailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Processed email not found: " + id));

        // Verificar que pertenece al tenant
        if (!email.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Processed email does not belong to current tenant");
        }

        return ResponseEntity.ok(ApiResponse.success(email));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Get polling statistics",
        description = "Get statistics about email processing for the current tenant"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Long tenantId = TenantContext.getTenantId();

        Map<String, Object> stats = new HashMap<>();

        // Contar por estado
        long totalEmails = processedEmailRepository.countByProcessingStatus(
                com.atina.invoice.api.model.enums.EmailProcessingStatus.COMPLETED);
        long pendingEmails = processedEmailRepository.countByProcessingStatus(
                com.atina.invoice.api.model.enums.EmailProcessingStatus.PENDING);
        long failedEmails = processedEmailRepository.countByProcessingStatus(
                com.atina.invoice.api.model.enums.EmailProcessingStatus.FAILED);
        long ignoredEmails = processedEmailRepository.countByProcessingStatus(
                com.atina.invoice.api.model.enums.EmailProcessingStatus.IGNORED);

        // Contar hoy
        java.time.Instant startOfDay = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant();
        long processedToday = processedEmailRepository.countProcessedToday(startOfDay);

        stats.put("total_completed", totalEmails);
        stats.put("pending", pendingEmails);
        stats.put("failed", failedEmails);
        stats.put("ignored", ignoredEmails);
        stats.put("processed_today", processedToday);
        stats.put("tenant_id", tenantId);

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
