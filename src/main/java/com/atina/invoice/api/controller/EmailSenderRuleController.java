package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.request.ImportSenderConfigRequest;
import com.atina.invoice.api.dto.request.UpdateEmailSenderRuleRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.EmailSenderRuleResponse;
import com.atina.invoice.api.service.EmailSenderRuleService;
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
 * REST Controller para gestión de reglas de sender
 */
@RestController
@RequestMapping("/api/v1/sender-rules")
@RequiredArgsConstructor
@Tag(name = "Sender Rules", description = "Email sender rule management")
@SecurityRequirement(name = "bearer-jwt")
public class EmailSenderRuleController {

    private final EmailSenderRuleService senderRuleService;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] List all sender rules",
        description = "Get all sender rules from all tenants"
    )
    public ResponseEntity<ApiResponse<List<EmailSenderRuleResponse>>> getAllRules() {
        long start = System.currentTimeMillis();
        List<EmailSenderRuleResponse> rules = senderRuleService.getAllRules();
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(rules, MDC.get("correlationId"), duration));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Get sender rule",
        description = "Get sender rule details from any tenant, including attachment processing rules"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> getRuleById(
            @Parameter(description = "Sender rule ID") @PathVariable Long id) {
        long start = System.currentTimeMillis();
        EmailSenderRuleResponse rule = senderRuleService.getRuleById(id);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(rule, MDC.get("correlationId"), duration));
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Create sender rule",
        description = "Create a new sender rule for any email account to define how to process emails from a specific sender"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> createRule(
            @Valid @RequestBody CreateEmailSenderRuleRequest request) {
        long start = System.currentTimeMillis();
        EmailSenderRuleResponse created = senderRuleService.createRule(request);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, MDC.get("correlationId"), duration));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Update sender rule",
        description = "Update sender rule configuration from any tenant (all fields optional)"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> updateRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long id,
            @Valid @RequestBody UpdateEmailSenderRuleRequest request) {
        long start = System.currentTimeMillis();
        EmailSenderRuleResponse updated = senderRuleService.updateRule(id, request);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(updated, MDC.get("correlationId"), duration));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Delete sender rule",
        description = "Permanently delete a sender rule from any tenant and all its attachment processing rules"
    )
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long id) {
        long start = System.currentTimeMillis();
        senderRuleService.deleteRule(id);
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok(ApiResponse.success(null, MDC.get("correlationId"), duration));
    }

}
