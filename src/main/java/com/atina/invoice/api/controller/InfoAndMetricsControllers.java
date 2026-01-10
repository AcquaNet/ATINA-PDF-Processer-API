// ============================================
// FILE: controller/InfoController.java
// ============================================

package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.InfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Info controller
 * Provides API and application information
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Info", description = "API information endpoints")
public class InfoController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.name:Invoice Extractor API}")
    private String appName;

    @Value("${app.description:AI-powered invoice data extraction engine}")
    private String appDescription;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Get API information
     * 
     * GET /api/v1/info
     * 
     * Returns information about the API, engine, and build
     */
    @GetMapping("/info")
    @Operation(
        summary = "Get API information",
        description = "Returns information about API version, engine capabilities, and build details"
    )
    public ApiResponse<InfoResponse> getInfo() {
        log.debug("API info requested");
        
        // Application info
        InfoResponse.Application application = InfoResponse.Application.builder()
            .name(appName)
            .version(appVersion)
            .description(appDescription)
            .profile(activeProfile)
            .build();
        
        // Build info
        InfoResponse.Build build = InfoResponse.Build.builder()
            .timestamp(Instant.now().toString())
            .commit("N/A")  // Can be injected from build process
            .branch("main")
            .build();
        
        // Engine info
        InfoResponse.Engine engine = InfoResponse.Engine.builder()
            .version("1.0.0")
            .supportedRuleTypes(getSupportedRuleTypes())
            .build();
        
        // API info
        InfoResponse.Api api = InfoResponse.Api.builder()
            .version("v1")
            .documentation("/swagger-ui.html")
            .build();
        
        // Build response
        InfoResponse response = InfoResponse.builder()
            .application(application)
            .build(build)
            .engine(engine)
            .api(api)
            .build();
        
        return ApiResponse.success(response, MDC.get("correlationId"), 0L);
    }

    /**
     * Get supported rule types
     */
    private List<String> getSupportedRuleTypes() {
        return Arrays.asList(
            "anchor_proximity",
            "region_anchor_proximity",
            "line_regex",
            "global_regex",
            "table_by_headers"
        );
    }
}

// ============================================
// FILE: controller/MetricsController.java
// ============================================

package com.atina.invoice.api.controller;

import ApiResponse;
import com.atina.invoice.api.service.MetricsService;
import Operation;
import Tag;
import RequiredArgsConstructor;
import Slf4j;
import MDC;
import GetMapping;
import RequestMapping;
import RestController;

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
    @org.springframework.web.bind.annotation.PostMapping("/metrics/reset")
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

// ============================================
// USAGE EXAMPLES - InfoController
// ============================================

/*
GET /api/v1/info

Response:
{
  "success": true,
  "correlationId": "api-20240110-123456-a1b2c3d4",
  "timestamp": "2024-01-10T12:34:56.789Z",
  "duration": 0,
  "data": {
    "application": {
      "name": "Invoice Extractor API",
      "version": "1.0.0",
      "description": "AI-powered invoice data extraction engine",
      "profile": "dev"
    },
    "build": {
      "timestamp": "2024-01-10T12:00:00.000Z",
      "commit": "abc123def",
      "branch": "main"
    },
    "engine": {
      "version": "1.0.0",
      "supportedRuleTypes": [
        "anchor_proximity",
        "region_anchor_proximity",
        "line_regex",
        "global_regex",
        "table_by_headers"
      ]
    },
    "api": {
      "version": "v1",
      "documentation": "/swagger-ui.html"
    }
  }
}

CURL Example:
curl http://localhost:8080/api/v1/info \
  -H "Authorization: Bearer $TOKEN"
*/

// ============================================
// USAGE EXAMPLES - MetricsController
// ============================================

/*
GET /api/v1/metrics

Response:
{
  "success": true,
  "correlationId": "api-20240110-123456-a1b2c3d4",
  "timestamp": "2024-01-10T12:34:56.789Z",
  "duration": 5,
  "data": {
    "extractions": {
      "total": 150,
      "success": 145,
      "failure": 5,
      "successRate": 96.67
    },
    "performance": {
      "averageDuration": 2500
    },
    "jobs": {
      "total": 50,
      "pending": 2,
      "processing": 3,
      "completed": 42,
      "failed": 3,
      "cancelled": 0
    },
    "docling": {
      "total": 100,
      "success": 98,
      "failure": 2,
      "successRate": 98.0,
      "averageDuration": 3500
    }
  }
}

CURL Example:
curl http://localhost:8080/api/v1/metrics \
  -H "Authorization: Bearer $TOKEN"
*/

// ============================================
// INTEGRATION WITH MONITORING SYSTEMS
// ============================================

/*
# Prometheus format (optional future enhancement)
GET /actuator/prometheus

# Grafana dashboard
- Create dashboard with metrics from /api/v1/metrics
- Visualize success rates, durations, job counts

# CloudWatch / DataDog
- Poll /api/v1/metrics endpoint
- Push metrics to monitoring system
- Set up alerts on success rate, duration, error counts

# Example monitoring script
#!/bin/bash
while true; do
  curl -s http://localhost:8080/api/v1/metrics \
    -H "Authorization: Bearer $TOKEN" \
    | jq '.data.extractions.successRate'
  sleep 60
done
*/
