package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateEmailAccountRequest;
import com.atina.invoice.api.dto.request.UpdateEmailAccountRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.EmailAccountResponse;
import com.atina.invoice.api.service.EmailAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] List email accounts",
        description = "Get all email accounts configured for the current tenant"
    )
    public ResponseEntity<ApiResponse<List<EmailAccountResponse>>> getAllAccounts() {
        List<EmailAccountResponse> accounts = emailAccountService.getAllAccounts();
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Get email account",
        description = "Get email account details by ID"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> getAccountById(
            @Parameter(description = "Email account ID") @PathVariable Long id) {
        EmailAccountResponse account = emailAccountService.getAccountById(id);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Create email account",
        description = "Create a new email account for monitoring. Supports IMAP and POP3."
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> createAccount(
            @Valid @RequestBody CreateEmailAccountRequest request) {
        EmailAccountResponse created = emailAccountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Update email account",
        description = "Update email account configuration (all fields optional)"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> updateAccount(
            @Parameter(description = "Email account ID") @PathVariable Long id,
            @Valid @RequestBody UpdateEmailAccountRequest request) {
        EmailAccountResponse updated = emailAccountService.updateAccount(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Delete email account",
        description = "Permanently delete an email account"
    )
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Parameter(description = "Email account ID") @PathVariable Long id) {
        emailAccountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/toggle-polling")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Toggle polling",
        description = "Enable or disable automatic email polling for this account"
    )
    public ResponseEntity<ApiResponse<EmailAccountResponse>> togglePolling(
            @Parameter(description = "Email account ID") @PathVariable Long id,
            @Parameter(description = "Enable polling") @RequestParam boolean enabled) {
        EmailAccountResponse updated = emailAccountService.togglePolling(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{id}/test-connection")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Test connection",
        description = "Test IMAP/POP3 connection to verify credentials and configuration"
    )
    public ResponseEntity<ApiResponse<String>> testConnection(
            @Parameter(description = "Email account ID") @PathVariable Long id) {
        String result = emailAccountService.testConnection(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
