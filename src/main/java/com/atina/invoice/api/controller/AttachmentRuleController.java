package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateAttachmentRuleRequest;
import com.atina.invoice.api.dto.request.UpdateAttachmentRuleRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.AttachmentProcessingRuleResponse;
import com.atina.invoice.api.service.AttachmentRuleService;
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
import java.util.Map;

/**
 * REST Controller para gestión de reglas de attachment
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Attachment Rules", description = "Attachment processing rule management")
@SecurityRequirement(name = "bearer-jwt")
public class AttachmentRuleController {

    private final AttachmentRuleService attachmentRuleService;

    @GetMapping("/sender-rules/{senderRuleId}/attachment-rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] List attachment rules",
        description = "Get all attachment processing rules for a sender rule"
    )
    public ResponseEntity<ApiResponse<List<AttachmentProcessingRuleResponse>>> getRulesBySenderRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long senderRuleId) {
        List<AttachmentProcessingRuleResponse> rules = attachmentRuleService.getRulesBySenderRule(senderRuleId);
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    @GetMapping("/attachment-rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Get attachment rule",
        description = "Get attachment processing rule details"
    )
    public ResponseEntity<ApiResponse<AttachmentProcessingRuleResponse>> getRuleById(
            @Parameter(description = "Attachment rule ID") @PathVariable Long id) {
        AttachmentProcessingRuleResponse rule = attachmentRuleService.getRuleById(id);
        return ResponseEntity.ok(ApiResponse.success(rule));
    }

    @PostMapping("/sender-rules/{senderRuleId}/attachment-rules")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Create attachment rule",
        description = "Create a new attachment processing rule with regex pattern matching"
    )
    public ResponseEntity<ApiResponse<AttachmentProcessingRuleResponse>> createRule(
            @Parameter(description = "Sender rule ID") @PathVariable Long senderRuleId,
            @Valid @RequestBody CreateAttachmentRuleRequest request) {
        AttachmentProcessingRuleResponse created = attachmentRuleService.createRule(senderRuleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/attachment-rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Update attachment rule",
        description = "Update attachment processing rule (all fields optional)"
    )
    public ResponseEntity<ApiResponse<AttachmentProcessingRuleResponse>> updateRule(
            @Parameter(description = "Attachment rule ID") @PathVariable Long id,
            @Valid @RequestBody UpdateAttachmentRuleRequest request) {
        AttachmentProcessingRuleResponse updated = attachmentRuleService.updateRule(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/attachment-rules/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[SYSTEM_ADMIN] Delete attachment rule",
        description = "Permanently delete an attachment processing rule"
    )
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @Parameter(description = "Attachment rule ID") @PathVariable Long id) {
        attachmentRuleService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/attachment-rules/{id}/reorder")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Reorder rule",
        description = "Change the execution order of an attachment rule"
    )
    public ResponseEntity<ApiResponse<AttachmentProcessingRuleResponse>> reorderRule(
            @Parameter(description = "Attachment rule ID") @PathVariable Long id,
            @Parameter(description = "New order number") @RequestParam Integer newOrder) {
        AttachmentProcessingRuleResponse reordered = attachmentRuleService.reorderRule(id, newOrder);
        return ResponseEntity.ok(ApiResponse.success(reordered));
    }

    @PostMapping("/attachment-rules/test-regex")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "[ADMIN] Test regex pattern",
        description = """
            Test a regex pattern against a list of filenames.
            Returns which filenames match the pattern.
            
            Example request:
            {
              "regex": "^Invoice+([0-9])+(.PDF|.pdf)$",
              "filenames": [
                "Invoice123.pdf",
                "Invoice456.PDF",
                "Report.pdf",
                "Invoice.txt"
              ]
            }
            
            Example response:
            {
              "valid": true,
              "regex": "^Invoice+([0-9])+(.PDF|.pdf)$",
              "matches": {
                "Invoice123.pdf": true,
                "Invoice456.PDF": true,
                "Report.pdf": false,
                "Invoice.txt": false
              },
              "totalFiles": 4,
              "matchedFiles": 2
            }
            """
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> testRegex(
            @Parameter(description = "Regex pattern to test") @RequestParam String regex,
            @Parameter(description = "List of filenames to test against") @RequestBody List<String> filenames) {
        Map<String, Object> result = attachmentRuleService.testRegex(regex, filenames);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
