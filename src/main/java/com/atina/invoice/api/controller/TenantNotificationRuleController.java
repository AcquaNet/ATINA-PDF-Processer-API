package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.TenantNotificationRule;
import com.atina.invoice.api.model.enums.NotificationChannel;
import com.atina.invoice.api.model.enums.NotificationEvent;
import com.atina.invoice.api.model.enums.NotificationRecipientType;
import com.atina.invoice.api.repository.TenantNotificationRuleRepository;
import com.atina.invoice.api.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/notification-rules")
@RequiredArgsConstructor
@Tag(name = "Notification Rule", description = "Notification Rule Configuration")
@SecurityRequirement(name = "bearer-jwt")
public class TenantNotificationRuleController {

    private final TenantNotificationRuleRepository ruleRepository;
    private final TenantRepository tenantRepository;

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] List notification rules",
            description = "Get all notification rules for a tenant"
    )
    public ResponseEntity<List<TenantNotificationRule>> listRules(@PathVariable Long tenantId) {
        List<TenantNotificationRule> rules = ruleRepository.findByTenantId(tenantId);
        return ResponseEntity.ok(rules);
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Create notification rule",
            description = "Create a new notification rule for a tenant (EMAIL, SLACK, API_WEBHOOK)"
    )
    public ResponseEntity<?> createRule(@PathVariable Long tenantId, @RequestBody Map<String, Object> body) {

        long start = System.currentTimeMillis();

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            TenantNotificationRule rule = TenantNotificationRule.builder()
                    .tenant(tenant)
                    .event(NotificationEvent.valueOf((String) body.get("event")))
                    .recipientType(NotificationRecipientType.valueOf((String) body.get("recipient_type")))
                    .channel(NotificationChannel.valueOf((String) body.get("channel")))
                    .channelConfig((String) body.get("channel_config"))
                    .templateName((String) body.get("template_name"))
                    .subjectTemplate((String) body.get("subject_template"))
                    .enabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true)
                    .build();

            rule = ruleRepository.save(rule);

            long duration = System.currentTimeMillis() - start;

            log.info("Created notification rule {} for tenant {}", rule.getId(), tenantId);

            return ResponseEntity.ok(ApiResponse.success(rule, MDC.get("correlationId"), duration));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid enum value: " + e.getMessage()));
        }
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Update notification rule",
            description = "Update an existing notification rule"
    )
    public ResponseEntity<?> updateRule(@PathVariable Long tenantId,
                                        @PathVariable Long ruleId,
                                        @RequestBody Map<String, Object> body) {

        long start = System.currentTimeMillis();

        TenantNotificationRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.notFound().build();
        }

        try {
            if (body.containsKey("event")) {
                rule.setEvent(NotificationEvent.valueOf((String) body.get("event")));
            }
            if (body.containsKey("recipient_type")) {
                rule.setRecipientType(NotificationRecipientType.valueOf((String) body.get("recipient_type")));
            }
            if (body.containsKey("channel")) {
                rule.setChannel(NotificationChannel.valueOf((String) body.get("channel")));
            }
            if (body.containsKey("channel_config")) {
                rule.setChannelConfig((String) body.get("channel_config"));
            }
            if (body.containsKey("template_name")) {
                rule.setTemplateName((String) body.get("template_name"));
            }
            if (body.containsKey("subject_template")) {
                rule.setSubjectTemplate((String) body.get("subject_template"));
            }
            if (body.containsKey("enabled")) {
                rule.setEnabled((Boolean) body.get("enabled"));
            }

            rule = ruleRepository.save(rule);

            long duration = System.currentTimeMillis() - start;

            log.info("Updated notification rule {} for tenant {}", ruleId, tenantId);

            return ResponseEntity.ok(ApiResponse.success(rule, MDC.get("correlationId"), duration));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid enum value: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "[SYSTEM_ADMIN] Delete notification rule",
            description = "Delete a notification rule"
    )
    public ResponseEntity<?> deleteRule(@PathVariable Long tenantId, @PathVariable Long ruleId) {

        long start = System.currentTimeMillis();

        TenantNotificationRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.getTenant().getId().equals(tenantId)) {
            return ResponseEntity.notFound().build();
        }

        ruleRepository.delete(rule);

        long duration = System.currentTimeMillis() - start;

        log.info("Deleted notification rule {} for tenant {}", ruleId, tenantId);

        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Rule deleted", "id", ruleId), MDC.get("correlationId"), duration));

    }
}
