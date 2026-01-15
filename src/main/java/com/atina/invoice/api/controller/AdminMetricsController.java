package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin Metrics Controller - FIXED VERSION
 * Endpoints for viewing metrics across all tenants (admin only)
 *
 * FIXES:
 * - Line 116: Changed Map<String, Long> to Map<String, Object> for getSystemMetrics()
 * - Line 53: Changed getAllMetrics() to getMetrics() for consistency
 * - Line 82: Changed getAllMetrics() to getMetrics() for consistency
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
@Tag(name = "Admin Metrics", description = "Admin endpoints for cross-tenant metrics")
public class AdminMetricsController {

    private final MetricsService metricsService;
    private final TenantRepository tenantRepository;

    /**
     * Get metrics for all tenants
     */
    @GetMapping("/tenants")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get metrics for all tenants", description = "Returns usage metrics grouped by tenant")
    public ApiResponse<Map<String, Object>> getAllTenantMetrics() {
        log.info("Fetching metrics for all tenants");

        List<Tenant> tenants = tenantRepository.findAll();

        Map<String, Object> result = new HashMap<>();

        for (Tenant tenant : tenants) {
            Map<String, Object> tenantData = new HashMap<>();
            tenantData.put("id", tenant.getId());
            tenantData.put("code", tenant.getTenantCode());
            tenantData.put("name", tenant.getTenantName());
            tenantData.put("subscriptionTier", tenant.getSubscriptionTier());
            tenantData.put("enabled", tenant.isEnabled());

            // FIXED: Use getMetrics() instead of getAllMetrics() for Map<String, Object>
            tenantData.put("metrics", metricsService.getMetrics(tenant.getId()));
            tenantData.put("totalApiCalls", metricsService.getTotalApiCalls(tenant.getId()));

            result.put(tenant.getTenantCode(), tenantData);
        }

        return ApiResponse.success(result);
    }

    /**
     * Get metrics for a specific tenant
     */
    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get metrics for specific tenant")
    public ApiResponse<Map<String, Object>> getTenantMetrics(@PathVariable Long tenantId) {
        log.info("Fetching metrics for tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        Map<String, Object> result = new HashMap<>();
        result.put("tenant", Map.of(
                "id", tenant.getId(),
                "code", tenant.getTenantCode(),
                "name", tenant.getTenantName(),
                "subscriptionTier", tenant.getSubscriptionTier(),
                "enabled", tenant.isEnabled()
        ));

        // FIXED: Use getMetrics() instead of getAllMetrics() for Map<String, Object>
        result.put("metrics", metricsService.getMetrics(tenantId));
        result.put("totalApiCalls", metricsService.getTotalApiCalls(tenantId));

        return ApiResponse.success(result);
    }

    /**
     * Get aggregated metrics for a specific metric key across all tenants
     */
    @GetMapping("/aggregated/{metricKey}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get aggregated metrics across tenants")
    public ApiResponse<Map<String, Object>> getAggregatedMetrics(@PathVariable String metricKey) {
        log.info("Fetching aggregated metrics for key: {}", metricKey);

        Map<String, Long> aggregated = metricsService.getAggregatedMetrics(metricKey);

        Map<String, Object> result = new HashMap<>();
        result.put("metricKey", metricKey);
        result.put("byTenant", aggregated);
        result.put("total", aggregated.values().stream().mapToLong(Long::longValue).sum());

        return ApiResponse.success(result);
    }

    /**
     * Get system-wide metrics (not tenant-specific)
     */
    @GetMapping("/system")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get system-wide metrics")
    public ApiResponse<Map<String, Object>> getSystemMetrics() {
        log.info("Fetching system-wide metrics");

        // FIXED: Changed from Map<String, Long> to Map<String, Object>
        Map<String, Object> metrics = metricsService.getSystemMetrics();

        return ApiResponse.success(metrics);
    }

    /**
     * Get tenant summary (counts, totals)
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get tenant usage summary")
    public ApiResponse<Map<String, Object>> getTenantSummary() {
        log.info("Fetching tenant summary");

        List<Tenant> tenants = tenantRepository.findAll();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTenants", tenants.size());
        summary.put("activeTenants", tenants.stream().filter(Tenant::isEnabled).count());

        Map<String, Long> apiCallsByTenant = tenants.stream()
                .collect(Collectors.toMap(
                        Tenant::getTenantCode,
                        t -> metricsService.getTotalApiCalls(t.getId())
                ));

        summary.put("apiCallsByTenant", apiCallsByTenant);
        summary.put("totalApiCalls", apiCallsByTenant.values().stream().mapToLong(Long::longValue).sum());

        return ApiResponse.success(summary);
    }

    /**
     * Reset metrics for a tenant (use with caution)
     */
    @DeleteMapping("/tenants/{tenantId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Reset metrics for a tenant")
    public ApiResponse<String> resetTenantMetrics(@PathVariable Long tenantId) {
        log.warn("Resetting metrics for tenant: {}", tenantId);

        metricsService.resetTenantMetrics(tenantId);

        return ApiResponse.success("Metrics reset successfully for tenant: " + tenantId);
    }

    /**
     * Get top N tenants by API calls
     */
    @GetMapping("/top-tenants")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get top tenants by API usage")
    public ApiResponse<Map<String, Object>> getTopTenants(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching top {} tenants by API calls", limit);

        List<Object[]> topTenants = metricsService.getTopTenantsByMetric(
                "api.calls.total",
                limit
        );

        Map<String, Object> result = new HashMap<>();
        result.put("limit", limit);
        result.put("topTenants", topTenants.stream()
                .map(row -> {
                    Map<String, Object> tenant = new HashMap<>();
                    tenant.put("tenantId", row[0]);
                    tenant.put("apiCalls", row[1]);
                    return tenant;
                })
                .collect(Collectors.toList())
        );

        return ApiResponse.success(result);
    }

    /**
     * Get cross-tenant metrics summary with statistics
     */
    @GetMapping("/cross-tenant-summary")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get statistical summary across all tenants")
    public ApiResponse<Map<String, Object>> getCrossTenantSummary() {
        log.info("Fetching cross-tenant metrics summary");

        List<Object[]> summary = metricsService.getCrossTenantSummary();

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary.stream()
                .map(row -> {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("metricKey", row[0]);
                    stat.put("tenantCount", row[1]);
                    stat.put("totalSum", row[2]);
                    stat.put("average", row[3]);
                    stat.put("min", row[4]);
                    stat.put("max", row[5]);
                    return stat;
                })
                .collect(Collectors.toList())
        );

        return ApiResponse.success(result);
    }
}
