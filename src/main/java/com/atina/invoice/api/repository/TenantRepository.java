package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tenant Repository - Complete version with all useful queries
 *
 * Provides comprehensive tenant management queries including:
 * - Basic CRUD operations (inherited from JpaRepository)
 * - Finding by code, status, subscription tier
 * - Monitoring and analytics queries
 * - Quota management helpers
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    // ==================== Basic Queries ====================

    /**
     * Find tenant by unique tenant code.
     * Used for routing requests and identifying tenants.
     *
     * @param tenantCode Unique tenant code (e.g., "ACME", "GLOBEX")
     * @return Optional containing tenant if found
     */
    Optional<Tenant> findByTenantCode(String tenantCode);

    /**
     * Check if tenant with given code exists.
     * Used for validation during tenant creation.
     *
     * @param tenantCode Tenant code to check
     * @return true if exists, false otherwise
     */
    boolean existsByTenantCode(String tenantCode);

    /**
     * Find all tenants by enabled status.
     *
     * @param enabled true for active tenants, false for disabled
     * @return List of tenants matching status
     */
    List<Tenant> findByEnabled(boolean enabled);

    /**
     * Find all active tenants (enabled = true).
     * Convenience method for common use case.
     *
     * @return List of active tenants
     */
    default List<Tenant> findAllActive() {
        return findByEnabled(true);
    }

    // ==================== Subscription Tier Queries ====================

    /**
     * Find tenants by subscription tier.
     * Useful for bulk operations or analytics by tier.
     *
     * @param subscriptionTier Subscription tier (FREE, BASIC, PREMIUM, ENTERPRISE)
     * @return List of tenants with given tier
     */
    List<Tenant> findBySubscriptionTier(String subscriptionTier);

    /**
     * Count tenants by subscription tier.
     *
     * @param subscriptionTier Subscription tier
     * @return Number of tenants in given tier
     */
    long countBySubscriptionTier(String subscriptionTier);

    /**
     * Find enabled tenants by subscription tier.
     *
     * @param enabled Enabled status
     * @param subscriptionTier Subscription tier
     * @return List of tenants matching criteria
     */
    List<Tenant> findByEnabledAndSubscriptionTier(boolean enabled, String subscriptionTier);

    // ==================== Quota Management Queries ====================

    /**
     * Find tenants that have API call quotas configured.
     * Used for monitoring and quota enforcement.
     *
     * @param enabled Only check enabled tenants
     * @return List of tenants with API call limits configured
     */
    List<Tenant> findByEnabledAndMaxApiCallsPerMonthIsNotNull(boolean enabled);

    /**
     * Find tenants with unlimited API calls (no quota).
     * These are typically ENTERPRISE tier tenants.
     *
     * @param enabled Only check enabled tenants
     * @return List of tenants with no API call limit
     */
    @Query("SELECT t FROM Tenant t WHERE t.enabled = :enabled AND t.maxApiCallsPerMonth IS NULL")
    List<Tenant> findByEnabledAndUnlimitedApiCalls(@Param("enabled") boolean enabled);

    /**
     * Find tenants with specific quota range.
     * Useful for identifying tenants in certain usage tiers.
     *
     * @param minCalls Minimum API calls per month
     * @param maxCalls Maximum API calls per month
     * @return List of tenants in quota range
     */
    @Query("SELECT t FROM Tenant t WHERE t.maxApiCallsPerMonth BETWEEN :minCalls AND :maxCalls")
    List<Tenant> findByApiCallQuotaRange(
            @Param("minCalls") Long minCalls,
            @Param("maxCalls") Long maxCalls);

    // ==================== Search and Filtering ====================

    /**
     * Find tenants by name pattern (case-insensitive).
     * Used for search and autocomplete.
     *
     * @param namePattern Name pattern (e.g., "%ACME%")
     * @return List of tenants matching pattern
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.tenantName) LIKE LOWER(:namePattern)")
    List<Tenant> findByTenantNameContainingIgnoreCase(@Param("namePattern") String namePattern);

    /**
     * Find tenants by contact email domain.
     * Useful for identifying tenants from same organization.
     *
     * @param domain Email domain (e.g., "acme.com")
     * @return List of tenants with matching email domain
     */
    @Query("SELECT t FROM Tenant t WHERE t.contactEmail LIKE CONCAT('%@', :domain)")
    List<Tenant> findByEmailDomain(@Param("domain") String domain);

    // ==================== Analytics and Reporting ====================

    /**
     * Count total active tenants.
     *
     * @return Number of active tenants
     */
    default long countActive() {
        return countByEnabled(true);
    }

    /**
     * Count tenants by enabled status.
     *
     * @param enabled Enabled status
     * @return Number of tenants
     */
    long countByEnabled(boolean enabled);

    /**
     * Find recently created tenants.
     * Useful for onboarding tracking and reporting.
     *
     * @param since Timestamp to check from
     * @return List of tenants created after given date
     */
    @Query("SELECT t FROM Tenant t WHERE t.createdAt >= :since ORDER BY t.createdAt DESC")
    List<Tenant> findRecentlyCreated(@Param("since") Instant since);

    /**
     * Find tenants by creation date range.
     * Useful for cohort analysis.
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of tenants created in date range
     */
    @Query("SELECT t FROM Tenant t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt")
    List<Tenant> findByCreatedAtBetween(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Get tenant counts by subscription tier.
     * Returns a summary for dashboard/reporting.
     *
     * @return List of [subscriptionTier, count] tuples
     */
    @Query("SELECT t.subscriptionTier, COUNT(t) FROM Tenant t GROUP BY t.subscriptionTier")
    List<Object[]> countByTierSummary();

    // ==================== Admin Operations ====================

    /**
     * Find all tenants that need quota monitoring.
     * Returns tenants with quotas that should be checked regularly.
     *
     * @return List of enabled tenants with quota limits
     */
    default List<Tenant> findTenantsRequiringQuotaMonitoring() {
        return findByEnabledAndMaxApiCallsPerMonthIsNotNull(true);
    }

    /**
     * Find tenants eligible for upgrade notifications.
     * Returns FREE and BASIC tier tenants for marketing.
     *
     * @return List of tenants in lower tiers
     */
    @Query("SELECT t FROM Tenant t WHERE t.enabled = true AND t.subscriptionTier IN ('FREE', 'BASIC')")
    List<Tenant> findEligibleForUpgrade();

    /**
     * Find stale tenants (not updated recently).
     * Useful for identifying potentially inactive accounts.
     *
     * @param since Timestamp to check
     * @return List of tenants not updated since given date
     */
    @Query("SELECT t FROM Tenant t WHERE t.updatedAt < :since OR t.updatedAt IS NULL")
    List<Tenant> findStaleAccounts(@Param("since") Instant since);
}
