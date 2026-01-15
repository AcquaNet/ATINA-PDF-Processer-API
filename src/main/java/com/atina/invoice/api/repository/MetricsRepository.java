package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.Metrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Metrics Repository - Complete version with advanced analytics queries
 *
 * Supports multi-tenant metrics with:
 * - Per-tenant metrics tracking
 * - System-wide metrics (tenantId = null)
 * - Time-based queries
 * - Aggregation queries
 * - Cleanup operations
 */
@Repository
public interface MetricsRepository extends JpaRepository<Metrics, Long> {

    // ==================== Basic Queries ====================

    /**
     * Find specific metric for a tenant.
     *
     * @param tenantId Tenant ID
     * @param metricKey Metric key (e.g., "api.calls.extract")
     * @return Optional containing metric if found
     */
    Optional<Metrics> findByTenantIdAndMetricKey(Long tenantId, String metricKey);

    /**
     * Find all metrics for a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of all metrics for tenant
     */
    List<Metrics> findByTenantId(Long tenantId);

    /**
     * Find all metrics with a specific key across all tenants.
     * Useful for cross-tenant analytics.
     *
     * @param metricKey Metric key
     * @return List of metrics with given key
     */
    List<Metrics> findByMetricKey(String metricKey);

    /**
     * Find system-wide metrics (no tenant).
     *
     * @return List of system metrics
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId IS NULL")
    List<Metrics> findSystemMetrics();

    /**
     * Find specific system metric.
     *
     * @param metricKey Metric key
     * @return Optional containing system metric if found
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId IS NULL AND m.metricKey = :metricKey")
    Optional<Metrics> findSystemMetric(@Param("metricKey") String metricKey);

    // ==================== Prefix and Pattern Queries ====================

    /**
     * Find metrics by tenant and metric key prefix.
     * Example: prefix "api.calls" returns all API call metrics.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     * @return List of metrics matching prefix
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey LIKE CONCAT(:prefix, '%')")
    List<Metrics> findByTenantIdAndMetricKeyStartingWith(
            @Param("tenantId") Long tenantId,
            @Param("prefix") String prefix);

    /**
     * Find metrics by metric key prefix across all tenants.
     *
     * @param prefix Metric key prefix
     * @return List of metrics matching prefix
     */
    @Query("SELECT m FROM Metrics m WHERE m.metricKey LIKE CONCAT(:prefix, '%')")
    List<Metrics> findByMetricKeyStartingWith(@Param("prefix") String prefix);

    /**
     * Find metrics by tenant and key pattern.
     *
     * @param tenantId Tenant ID
     * @param pattern SQL LIKE pattern (e.g., "api.calls.%")
     * @return List of metrics matching pattern
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey LIKE :pattern")
    List<Metrics> findByTenantIdAndMetricKeyPattern(
            @Param("tenantId") Long tenantId,
            @Param("pattern") String pattern);

    // ==================== Aggregation Queries ====================

    /**
     * Sum metric values by tenant and prefix.
     * Example: Sum all "api.calls.*" metrics for a tenant.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     * @return Sum of metric values (0 if none found)
     */
    @Query("SELECT COALESCE(SUM(m.metricValue), 0) FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey LIKE CONCAT(:prefix, '%')")
    Long sumMetricValueByTenantIdAndMetricKeyPrefix(
            @Param("tenantId") Long tenantId,
            @Param("prefix") String prefix);

    /**
     * Sum specific metric across all tenants.
     * Useful for global totals.
     *
     * @param metricKey Metric key
     * @return Sum of metric values across all tenants
     */
    @Query("SELECT COALESCE(SUM(m.metricValue), 0) FROM Metrics m WHERE m.metricKey = :metricKey AND m.tenantId IS NOT NULL")
    Long sumMetricValueByKey(@Param("metricKey") String metricKey);

    /**
     * Count metrics by tenant.
     *
     * @param tenantId Tenant ID
     * @return Number of metrics for tenant
     */
    long countByTenantId(Long tenantId);

    /**
     * Get average metric value by key across tenants.
     *
     * @param metricKey Metric key
     * @return Average value
     */
    @Query("SELECT AVG(m.metricValue) FROM Metrics m WHERE m.metricKey = :metricKey AND m.tenantId IS NOT NULL")
    Double averageMetricValueByKey(@Param("metricKey") String metricKey);

    /**
     * Get max metric value by key.
     *
     * @param metricKey Metric key
     * @return Maximum value
     */
    @Query("SELECT MAX(m.metricValue) FROM Metrics m WHERE m.metricKey = :metricKey")
    Long maxMetricValueByKey(@Param("metricKey") String metricKey);

    /**
     * Get min metric value by key.
     *
     * @param metricKey Metric key
     * @return Minimum value
     */
    @Query("SELECT MIN(m.metricValue) FROM Metrics m WHERE m.metricKey = :metricKey")
    Long minMetricValueByKey(@Param("metricKey") String metricKey);

    // ==================== Time-Based Queries ====================

    /**
     * Find metrics created after a certain time.
     * Useful for recent activity monitoring.
     *
     * @param tenantId Tenant ID
     * @param since Timestamp
     * @return List of metrics created after given time
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId = :tenantId AND m.createdAt >= :since")
    List<Metrics> findByTenantIdCreatedAfter(
            @Param("tenantId") Long tenantId,
            @Param("since") Instant since);

    /**
     * Find metrics in date range.
     *
     * @param tenantId Tenant ID
     * @param startDate Start of range
     * @param endDate End of range
     * @return List of metrics in range
     */
    @Query("SELECT m FROM Metrics m WHERE m.tenantId = :tenantId AND m.createdAt BETWEEN :startDate AND :endDate")
    List<Metrics> findByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Sum metric values for a time period.
     * Example: Total API calls in last 30 days.
     *
     * @param tenantId Tenant ID
     * @param metricKey Metric key
     * @param since Start of period
     * @return Sum of values in period
     */
    @Query("SELECT COALESCE(SUM(m.metricValue), 0) FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey = :metricKey AND m.createdAt >= :since")
    Long sumMetricValueByPeriod(
            @Param("tenantId") Long tenantId,
            @Param("metricKey") String metricKey,
            @Param("since") Instant since);

    // ==================== Top/Bottom Queries ====================

    /**
     * Find top N tenants by metric value.
     * Example: Top 10 API users.
     *
     * @param metricKey Metric key
     * @param limit Number of results
     * @return List of [tenantId, metricValue] tuples
     */
    @Query(value = "SELECT tenant_id, metric_value FROM metrics WHERE metric_key = :metricKey AND tenant_id IS NOT NULL ORDER BY metric_value DESC LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findTopTenantsByMetric(
            @Param("metricKey") String metricKey,
            @Param("limit") int limit);

    /**
     * Find tenants exceeding threshold.
     * Useful for quota monitoring.
     *
     * @param metricKey Metric key
     * @param threshold Threshold value
     * @return List of tenant IDs exceeding threshold
     */
    @Query("SELECT m.tenantId FROM Metrics m WHERE m.metricKey = :metricKey AND m.metricValue > :threshold AND m.tenantId IS NOT NULL")
    List<Long> findTenantsExceedingThreshold(
            @Param("metricKey") String metricKey,
            @Param("threshold") Long threshold);

    // ==================== Cleanup Operations ====================

    /**
     * Delete all metrics for a tenant.
     * Used for tenant cleanup or metrics reset.
     *
     * @param tenantId Tenant ID
     */
    @Modifying
    @Query("DELETE FROM Metrics m WHERE m.tenantId = :tenantId")
    void deleteByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Delete metrics older than given date.
     * Used for data retention policies.
     *
     * @param before Cutoff date
     */
    @Modifying
    @Query("DELETE FROM Metrics m WHERE m.createdAt < :before")
    void deleteOlderThan(@Param("before") Instant before);

    /**
     * Delete specific metric for tenant.
     *
     * @param tenantId Tenant ID
     * @param metricKey Metric key
     */
    @Modifying
    @Query("DELETE FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey = :metricKey")
    void deleteByTenantIdAndMetricKey(
            @Param("tenantId") Long tenantId,
            @Param("metricKey") String metricKey);

    /**
     * Delete metrics by key prefix for tenant.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     */
    @Modifying
    @Query("DELETE FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey LIKE CONCAT(:prefix, '%')")
    void deleteByTenantIdAndPrefix(
            @Param("tenantId") Long tenantId,
            @Param("prefix") String prefix);

    // ==================== Bulk Operations ====================

    /**
     * Get summary of all metrics for a tenant.
     * Returns list of [metricKey, metricValue] tuples.
     *
     * @param tenantId Tenant ID
     * @return List of metric summaries
     */
    @Query("SELECT m.metricKey, m.metricValue FROM Metrics m WHERE m.tenantId = :tenantId ORDER BY m.metricKey")
    List<Object[]> getMetricsSummaryByTenant(@Param("tenantId") Long tenantId);

    /**
     * Get metrics summary by prefix for tenant.
     * Groups metrics by prefix and sums values.
     *
     * @param tenantId Tenant ID
     * @param prefix Metric key prefix
     * @return List of [metricKey, sum] tuples
     */
    @Query("SELECT m.metricKey, SUM(m.metricValue) FROM Metrics m WHERE m.tenantId = :tenantId AND m.metricKey LIKE CONCAT(:prefix, '%') GROUP BY m.metricKey")
    List<Object[]> getMetricsSummaryByPrefix(
            @Param("tenantId") Long tenantId,
            @Param("prefix") String prefix);

    /**
     * Get cross-tenant metrics summary.
     * Returns [metricKey, count, sum, avg, min, max] for each metric.
     *
     * @return List of metric statistics
     */
    @Query("SELECT m.metricKey, COUNT(m), SUM(m.metricValue), AVG(m.metricValue), MIN(m.metricValue), MAX(m.metricValue) FROM Metrics m WHERE m.tenantId IS NOT NULL GROUP BY m.metricKey")
    List<Object[]> getCrossTenantMetricsSummary();
}
