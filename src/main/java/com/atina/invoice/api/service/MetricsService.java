package com.atina.invoice.api.service;

import com.atina.invoice.api.model.Metrics;
import com.atina.invoice.api.repository.MetricsRepository;
import com.atina.invoice.api.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metrics Service - Complete version with multi-tenancy support
 *
 * Provides comprehensive metrics tracking with:
 * - Per-tenant metrics
 * - System-wide metrics
 * - Time-based queries
 * - Aggregations
 * - Cleanup operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsRepository metricsRepository;

    // ==================== Increment Operations ====================

    /**
     * Increment a metric for the current tenant by 1.
     * Most common usage: metricsService.increment("api.calls.extract");
     *
     * @param metricKey Metric key (e.g., "api.calls.extract")
     */
    @Transactional
    public void increment(String metricKey) {
        increment(metricKey, 1L);
    }

    /**
     * Increment a metric by a specific value for the current tenant.
     *
     * @param metricKey Metric key
     * @param value Amount to increment by
     */
    @Transactional
    public void increment(String metricKey, long value) {
        Long tenantId = TenantContext.getTenantId();
        increment(metricKey, value, tenantId);
    }

    /**
     * Increment a metric for a specific tenant.
     * Used by admin operations or background jobs.
     *
     * @param metricKey Metric key
     * @param value Amount to increment by
     * @param tenantId Tenant ID
     */
    @Transactional
    public void increment(String metricKey, long value, Long tenantId) {
        try {
            Metrics metric = metricsRepository
                    .findByTenantIdAndMetricKey(tenantId, metricKey)
                    .orElse(Metrics.builder()
                            .tenantId(tenantId)
                            .metricKey(metricKey)
                            .metricValue(0L)
                            .createdAt(Instant.now())
                            .build());

            metric.setMetricValue(metric.getMetricValue() + value);
            metric.setUpdatedAt(Instant.now());

            metricsRepository.save(metric);

            log.debug("Metric incremented: tenant={}, key={}, newValue={}",
                    tenantId, metricKey, metric.getMetricValue());

        } catch (Exception e) {
            log.error("Failed to increment metric: tenant={}, key={}", tenantId, metricKey, e);
        }
    }

    /**
     * Increment a system-wide metric (not tenant-specific) by 1.
     *
     * @param metricKey Metric key
     */
    @Transactional
    public void incrementSystem(String metricKey) {
        incrementSystem(metricKey, 1L);
    }

    /**
     * Increment a system-wide metric by a specific value.
     *
     * @param metricKey Metric key
     * @param value Amount to increment by
     */
    @Transactional
    public void incrementSystem(String metricKey, long value) {
        increment(metricKey, value, null);
    }

    // ==================== Get Single Metric ====================

    /**
     * Get a specific metric value for the current tenant.
     *
     * @param metricKey Metric key
     * @return Metric value, or 0 if not found
     */
    public long getMetric(String metricKey) {
        Long tenantId = TenantContext.getTenantId();
        return getMetric(metricKey, tenantId);
    }

    /**
     * Get a specific metric value for a specific tenant.
     *
     * @param metricKey Metric key
     * @param tenantId Tenant ID
     * @return Metric value, or 0 if not found
     */
    public long getMetric(String metricKey, Long tenantId) {
        return metricsRepository
                .findByTenantIdAndMetricKey(tenantId, metricKey)
                .map(Metrics::getMetricValue)
                .orElse(0L);
    }

    // ==================== Get All Metrics (Map<String, Object>) ====================

    /**
     * Get all metrics for the current tenant as Map<String, Object>.
     * This is the method commonly used in controllers/APIs.
     * Returns Long values as Objects for JSON serialization flexibility.
     *
     * @return Map of metric key → value
     */
    public Map<String, Object> getMetrics() {
        Long tenantId = TenantContext.getTenantId();
        return getMetrics(tenantId);
    }

    /**
     * Get all metrics for a specific tenant as Map<String, Object>.
     *
     * @param tenantId Tenant ID
     * @return Map of metric key → value
     */
    public Map<String, Object> getMetrics(Long tenantId) {
        List<Metrics> metrics = metricsRepository.findByTenantId(tenantId);
        Map<String, Object> result = new HashMap<>();

        for (Metrics metric : metrics) {
            result.put(metric.getMetricKey(), metric.getMetricValue());
        }

        return result;
    }

    // ==================== Get All Metrics (Map<String, Long>) ====================

    /**
     * Get all metrics for the current tenant as Map<String, Long>.
     * Type-safe version for internal use.
     *
     * @return Map of metric key → value
     */
    public Map<String, Long> getAllMetrics() {
        Long tenantId = TenantContext.getTenantId();
        return getAllMetrics(tenantId);
    }

    /**
     * Get all metrics for a specific tenant as Map<String, Long>.
     *
     * @param tenantId Tenant ID
     * @return Map of metric key → value
     */
    public Map<String, Long> getAllMetrics(Long tenantId) {
        List<Metrics> metrics = metricsRepository.findByTenantId(tenantId);
        return metrics.stream()
                .collect(Collectors.toMap(
                        Metrics::getMetricKey,
                        Metrics::getMetricValue
                ));
    }

    // ==================== System and Aggregated Metrics ====================

    /**
     * Get system-wide metrics (tenantId = null).
     *
     * @return Map of system metric key → value
     */
    public Map<String, Object> getSystemMetrics() {
        List<Metrics> metrics = metricsRepository.findSystemMetrics();
        Map<String, Object> result = new HashMap<>();

        for (Metrics metric : metrics) {
            result.put(metric.getMetricKey(), metric.getMetricValue());
        }

        return result;
    }

    /**
     * Get a specific metric across all tenants.
     * Returns map of tenantId → value.
     *
     * @param metricKey Metric key
     * @return Map of tenant ID → value
     */
    public Map<String, Long> getAggregatedMetrics(String metricKey) {
        List<Metrics> metrics = metricsRepository.findByMetricKey(metricKey);
        return metrics.stream()
                .filter(m -> m.getTenantId() != null)
                .collect(Collectors.toMap(
                        m -> "tenant_" + m.getTenantId(),
                        Metrics::getMetricValue
                ));
    }

    /**
     * Get sum of a metric across all tenants.
     *
     * @param metricKey Metric key
     * @return Total sum across all tenants
     */
    public long getAggregatedSum(String metricKey) {
        return metricsRepository.sumMetricValueByKey(metricKey);
    }

    /**
     * Get average of a metric across all tenants.
     *
     * @param metricKey Metric key
     * @return Average value, or 0.0 if no data
     */
    public double getAggregatedAverage(String metricKey) {
        Double avg = metricsRepository.averageMetricValueByKey(metricKey);
        return avg != null ? avg : 0.0;
    }

    // ==================== Specific Metric Aggregations ====================

    /**
     * Get total API calls for current tenant.
     * Sums all metrics starting with "api.calls."
     *
     * @return Total API calls
     */
    public long getTotalApiCalls() {
        Long tenantId = TenantContext.getTenantId();
        return getTotalApiCalls(tenantId);
    }

    /**
     * Get total API calls for specific tenant.
     *
     * @param tenantId Tenant ID
     * @return Total API calls
     */
    public long getTotalApiCalls(Long tenantId) {
        return metricsRepository.sumMetricValueByTenantIdAndMetricKeyPrefix(
                tenantId, "api.calls.");
    }

    /**
     * Get metrics by prefix for current tenant.
     * Example: getMetricsByPrefix("api.calls.") returns all API call metrics.
     *
     * @param prefix Metric key prefix
     * @return Map of metric key → value
     */
    public Map<String, Long> getMetricsByPrefix(String prefix) {
        Long tenantId = TenantContext.getTenantId();
        return getMetricsByPrefix(tenantId, prefix);
    }

    /**
     * Get metrics by prefix for specific tenant.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     * @return Map of metric key → value
     */
    public Map<String, Long> getMetricsByPrefix(Long tenantId, String prefix) {
        List<Metrics> metrics = metricsRepository.findByTenantIdAndMetricKeyStartingWith(
                tenantId, prefix);
        return metrics.stream()
                .collect(Collectors.toMap(
                        Metrics::getMetricKey,
                        Metrics::getMetricValue
                ));
    }

    /**
     * Sum metrics by prefix for current tenant.
     *
     * @param prefix Metric key prefix
     * @return Sum of all metrics with given prefix
     */
    public long sumByPrefix(String prefix) {
        Long tenantId = TenantContext.getTenantId();
        return sumByPrefix(tenantId, prefix);
    }

    /**
     * Sum metrics by prefix for specific tenant.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     * @return Sum of all metrics with given prefix
     */
    public long sumByPrefix(Long tenantId, String prefix) {
        return metricsRepository.sumMetricValueByTenantIdAndMetricKeyPrefix(
                tenantId, prefix);
    }

    // ==================== Time-Based Queries ====================

    /**
     * Get metrics created in the last N days for current tenant.
     *
     * @param days Number of days to look back
     * @return Map of metric key → value
     */
    public Map<String, Long> getMetricsLastNDays(int days) {
        Long tenantId = TenantContext.getTenantId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Metrics> metrics = metricsRepository.findByTenantIdCreatedAfter(
                tenantId, since);

        return metrics.stream()
                .collect(Collectors.toMap(
                        Metrics::getMetricKey,
                        Metrics::getMetricValue
                ));
    }

    /**
     * Get sum of a metric for a time period.
     *
     * @param metricKey Metric key
     * @param since Start of period
     * @return Sum of metric in period
     */
    public long getMetricSumSince(String metricKey, Instant since) {
        Long tenantId = TenantContext.getTenantId();
        return metricsRepository.sumMetricValueByPeriod(tenantId, metricKey, since);
    }

    /**
     * Get API calls in last 30 days for current tenant.
     *
     * @return Total API calls in last 30 days
     */
    public long getApiCallsLast30Days() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        return getMetricSumSince("api.calls.total", thirtyDaysAgo);
    }

    // ==================== Monitoring and Alerting ====================

    /**
     * Check if tenant has exceeded a percentage of their quota.
     *
     * @param tenantId Tenant ID
     * @param quota Monthly quota
     * @param thresholdPercent Threshold percentage (e.g., 90.0 for 90%)
     * @return true if exceeded threshold
     */
    public boolean hasExceededQuotaThreshold(Long tenantId, long quota, double thresholdPercent) {
        long usage = getTotalApiCalls(tenantId);
        long threshold = (long) (quota * (thresholdPercent / 100.0));
        return usage >= threshold;
    }

    /**
     * Get quota usage percentage for tenant.
     *
     * @param tenantId Tenant ID
     * @param quota Monthly quota
     * @return Usage percentage (0-100+)
     */
    public double getQuotaUsagePercent(Long tenantId, long quota) {
        if (quota == 0) return 0.0;
        long usage = getTotalApiCalls(tenantId);
        return (usage * 100.0) / quota;
    }

    /**
     * Get tenants exceeding a threshold for a specific metric.
     *
     * @param metricKey Metric key
     * @param threshold Threshold value
     * @return List of tenant IDs exceeding threshold
     */
    public List<Long> getTenantsExceedingThreshold(String metricKey, long threshold) {
        return metricsRepository.findTenantsExceedingThreshold(metricKey, threshold);
    }

    // ==================== Admin Operations ====================

    /**
     * Get metrics summary for a tenant.
     * Returns map with metadata and all metrics.
     *
     * @param tenantId Tenant ID
     * @return Summary map
     */
    public Map<String, Object> getMetricsSummary(Long tenantId) {
        Map<String, Object> summary = new HashMap<>();

        // Basic info
        summary.put("tenantId", tenantId);
        summary.put("timestamp", Instant.now().toString());

        // Metrics
        Map<String, Long> metrics = getAllMetrics(tenantId);
        summary.put("metrics", metrics);

        // Aggregations
        long totalApiCalls = getTotalApiCalls(tenantId);
        summary.put("totalApiCalls", totalApiCalls);

        long metricCount = metricsRepository.countByTenantId(tenantId);
        summary.put("metricCount", metricCount);

        return summary;
    }

    /**
     * Get top N tenants by a specific metric.
     *
     * @param metricKey Metric key
     * @param limit Number of results
     * @return List of [tenantId, value] tuples
     */
    public List<Object[]> getTopTenantsByMetric(String metricKey, int limit) {
        return metricsRepository.findTopTenantsByMetric(metricKey, limit);
    }

    /**
     * Reset all metrics for current tenant.
     * Admin operation - use with caution!
     */
    @Transactional
    public void resetMetrics() {
        Long tenantId = TenantContext.getTenantId();
        resetTenantMetrics(tenantId);
    }

    /**
     * Reset all metrics for a specific tenant.
     * Admin operation - use with caution!
     *
     * @param tenantId Tenant ID
     */
    @Transactional
    public void resetTenantMetrics(Long tenantId) {
        metricsRepository.deleteByTenantId(tenantId);
        log.info("Reset all metrics for tenant: {}", tenantId);
    }

    /**
     * Delete old metrics (data retention).
     * Admin operation - typically run as scheduled job.
     *
     * @param before Delete metrics older than this timestamp
     * @return Number of metrics deleted (if supported by implementation)
     */
    @Transactional
    public void deleteMetricsOlderThan(Instant before) {
        metricsRepository.deleteOlderThan(before);
        log.info("Deleted metrics older than: {}", before);
    }

    /**
     * Get cross-tenant metrics summary.
     * Returns statistics for each metric across all tenants.
     *
     * @return List of metric statistics
     */
    public List<Object[]> getCrossTenantSummary() {
        return metricsRepository.getCrossTenantMetricsSummary();
    }

    // ==================== Convenience Methods for Controllers ====================

    /**
     * Record successful extraction operation.
     * Convenience method for tracking extraction success.
     */
    @Transactional
    public void recordExtractionSuccess() {
        increment("api.calls.extract.success");
        increment("api.calls.extract");
        increment("api.calls.total");
    }

    /**
     * Record failed extraction operation.
     * Convenience method for tracking extraction failures.
     */
    @Transactional
    public void recordExtractionFailure() {
        increment("api.calls.extract.failure");
        increment("api.calls.extract");
        increment("api.calls.total");
    }

    /**
     * Record validation operation.
     */
    @Transactional
    public void recordValidation() {
        increment("api.calls.validate");
        increment("api.calls.total");
    }

    /**
     * Record AI generation operation.
     */
    @Transactional
    public void recordAiGeneration() {
        increment("api.calls.ai.generate");
        increment("api.calls.total");
    }

    /**
     * Record batch operation.
     */
    @Transactional
    public void recordBatchOperation(int itemCount) {
        increment("api.calls.batch");
        increment("api.calls.batch.items", itemCount);
        increment("api.calls.total");
    }


}
