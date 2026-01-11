package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Metrics controller
 * Provides usage metrics and statistics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Metrics and statistics endpoints")
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Get metrics
     * 
     * GET /api/v1/metrics
     * 
     * Returns usage metrics including:
     * - Extraction statistics
     * - Docling conversion statistics
     * - Job statistics
     * - Performance metrics
     */
    @GetMapping("/metrics")
    @Operation(
        summary = "Get usage metrics",
        description = "Returns statistics about extractions, conversions, jobs, and performance"
    )
    public ApiResponse<Map<String, Object>> getMetrics() {
        log.debug("Metrics requested");
        
        long start = System.currentTimeMillis();
        
        // Get metrics from service
        Map<String, Object> metrics = metricsService.getMetrics();
        
        long duration = System.currentTimeMillis() - start;
        
        return ApiResponse.success(metrics, MDC.get("correlationId"), duration);
    }

    /**
     * Reset metrics (optional - for testing)
     * 
     * POST /api/v1/metrics/reset
     * 
     * Resets all metrics counters (useful for testing)
     */
    @PostMapping("/metrics/reset")
    @Operation(
        summary = "Reset metrics",
        description = "Reset all metrics counters (for testing purposes)"
    )
    public ApiResponse<Void> resetMetrics() {
        log.info("Metrics reset requested");
        
        // TODO: Implement reset in MetricsService if needed
        // metricsService.resetMetrics();
        
        return ApiResponse.success(null, MDC.get("correlationId"), 0L);
    }
}
