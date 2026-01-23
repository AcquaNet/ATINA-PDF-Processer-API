package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.dto.response.EmailAccountsByTenantResponse;
import com.atina.invoice.api.service.EmailAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller para gestión de cuentas de email
 */
@RestController
@RequestMapping("/api/v1/email-accounts")
@RequiredArgsConstructor
@Tag(name = "Email Accounts", description = "Email account management for polling")
@SecurityRequirement(name = "bearer-jwt")
public class EmailAccountController {

    private final EmailAccountService emailAccountService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] List all email accounts",
        description = "Get all email accounts from all tenants"
    )
    public ResponseEntity<ApiResponse<List<EmailAccountResponse>>> getAllAccounts() {

        long start = System.currentTimeMillis();

        List<EmailAccountResponse> accounts = emailAccountService.getAllAccounts();

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(accounts, MDC.get("correlationId"), duration));
    }

    @GetMapping("/by-tenant")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] List email accounts grouped by tenant",
        description = "Get all email accounts grouped by tenant with summary information"
    )
    public ResponseEntity<ApiResponse<List<EmailAccountsByTenantResponse>>> getAccountsByTenant() {

        long start = System.currentTimeMillis();

        List<EmailAccountsByTenantResponse> accountsByTenant = emailAccountService.getAccountsByTenant();

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(accountsByTenant, MDC.get("correlationId"), duration));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Get email account",
        description = "Get email account details by ID from any tenant"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> getAccountById(
            @Parameter(description = "Email account ID") @PathVariable Long id) {

        long start = System.currentTimeMillis();

        EmailAccountResponse account = emailAccountService.getAccountById(id);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(account, MDC.get("correlationId"), duration));

    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Create email account",
        description = "Create a new email account for monitoring. Supports IMAP and POP3. Tenant ID must be provided in the request."
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> createAccount(
            @Valid @RequestBody CreateEmailAccountRequest request) {

        long start = System.currentTimeMillis();

        EmailAccountResponse created = emailAccountService.createAccount(request);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, MDC.get("correlationId"), duration));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Update email account",
        description = "Update email account configuration from any tenant (all fields optional)"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> updateAccount(
            @Parameter(description = "Email account ID") @PathVariable Long id,
            @Valid @RequestBody UpdateEmailAccountRequest request) {

        long start = System.currentTimeMillis();

        EmailAccountResponse updated = emailAccountService.updateAccount(id, request);

        long duration = System.currentTimeMillis() - start;

        return ResponseEntity.ok(ApiResponse.success(updated, MDC.get("correlationId"), duration));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Delete email account",
        description = "Permanently delete an email account"
    )
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Parameter(description = "Email account ID") @PathVariable Long id) {
        long start = System.currentTimeMillis();
        emailAccountService.deleteAccount(id);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(null, MDC.get("correlationId"), duration));
    }

    @PatchMapping("/{id}/toggle-polling")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Toggle polling",
        description = "Enable or disable automatic email polling for any account from any tenant"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> togglePolling(
            @Parameter(description = "Email account ID") @PathVariable Long id,
            @Parameter(description = "Enable polling") @RequestParam boolean enabled) {
        long start = System.currentTimeMillis();
        EmailAccountResponse updated = emailAccountService.togglePolling(id, enabled);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(updated, MDC.get("correlationId"), duration));
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Test connection",
        description = "Test IMAP/POP3 connection to verify credentials and configuration for any account from any tenant"
    )
    public ResponseEntity<ApiResponse<String>> testConnection(
            @Parameter(description = "Email account ID") @PathVariable Long id) {
        long start = System.currentTimeMillis();
        String result = emailAccountService.testConnection(id);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("correlationId"), duration));
    }
}
