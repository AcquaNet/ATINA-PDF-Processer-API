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
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] List sender rules",
        description = "Get all sender rules configured for the current tenant"
    )
    public ResponseEntity<ApiResponse<List<EmailSenderRuleResponse>>> getAllRules() {
        List<EmailSenderRuleResponse> rules = senderRuleService.getAllRules();
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Get sender rule",
        description = "Get sender rule details including attachment processing rules"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> getRuleById(
            @Parameter(description = "Sender rule ID") @PathVariable Long id) {
        EmailSenderRuleResponse rule = senderRuleService.getRuleById(id);
        return ResponseEntity.ok(ApiResponse.success(rule));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Create sender rule",
        description = "Create a new sender rule to define how to process emails from a specific sender"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> createRule(
            @Valid @RequestBody CreateEmailSenderRuleRequest request) {
        EmailSenderRuleResponse created = senderRuleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Update sender rule",
        description = "Update sender rule configuration (all fields optional)"
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> updateRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long id,
            @Valid @RequestBody UpdateEmailSenderRuleRequest request) {
        EmailSenderRuleResponse updated = senderRuleService.updateRule(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Delete sender rule",
        description = "Permanently delete a sender rule and all its attachment processing rules"
    )
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long id) {
        senderRuleService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/import-json")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Import from JSON",
        description = """
            Import sender configuration from JSON format (Mulesoft compatible).
            Creates sender rule and all attachment processing rules in one operation.
            
            Example JSON:
            {
              "email": "sender@example.com",
              "id": "92455890",
              "templates": {
                "email-received": "reply-mail-received.html",
                "email-processed": "reply-mail-processed.html"
              },
              "rules": [
                {
                  "id": 1,
                  "fileRule": "^Invoice+([0-9])+(.PDF|.pdf)$",
                  "source": "invoice",
                  "destination": "jde",
                  "metodo": ""
                }
              ]
            }
            """
    )
    public ResponseEntity<ApiResponse<EmailSenderRuleResponse>> importFromJson(
            @Parameter(description = "Email account ID") @RequestParam Long emailAccountId,
            @Valid @RequestBody ImportSenderConfigRequest config) {
        EmailSenderRuleResponse imported = senderRuleService.importFromJson(emailAccountId, config);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(imported));
    }
}
