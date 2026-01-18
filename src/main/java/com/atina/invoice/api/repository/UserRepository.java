package com.atina.invoice.api.repository;

import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User Repository - Enhanced with multi-tenancy support
 *
 * Each user belongs to exactly one tenant.
 * Provides queries to find users by tenant, username, and other criteria.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username (unique across all tenants).
     * Used for login authentication.
     *
     * @param username Username to search for
     * @return Optional containing user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username exists.
     *
     * @param username Username to check
     * @return true if exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find user by email (should be unique).
     *
     * @param email Email to search for
     * @return Optional containing user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email exists.
     *
     * @param email Email to check
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find all users belonging to a tenant.
     *
     * @param tenantId Tenant ID
     * @return List of users in the tenant
     */
    List<User> findByTenantId(Long tenantId);

    /**
     * Find enabled users in a tenant.
     *
     * @param tenantId Tenant ID
     * @param enabled true for enabled users, false for disabled
     * @return List of users matching criteria
     */
    List<User> findByTenantIdAndEnabled(Long tenantId, boolean enabled);

    /**
     * Find users by role in a tenant.
     *
     * @param tenantId Tenant ID
     * @param role User role (ADMIN, USER)
     * @return List of users matching criteria
     */
    List<User> findByTenantIdAndRole(Long tenantId, String role);

    /**
     * Count users in a tenant.
     *
     * @param tenantId Tenant ID
     * @return Number of users in tenant
     */
    long countByTenantId(Long tenantId);

    /**
     * Count enabled users in a tenant.
     *
     * @param tenantId Tenant ID
     * @param enabled true for enabled, false for disabled
     * @return Number of users matching criteria
     */
    long countByTenantIdAndEnabled(Long tenantId, boolean enabled);

    /**
     * Find admin users in a tenant.
     * Convenience method for common use case.
     *
     * @param tenantId Tenant ID
     * @return List of admin users in tenant
     */
    default List<User> findAdminsByTenant(Long tenantId) {
        return findByTenantIdAndRole(tenantId, "ADMIN");
    }

    /**
     * Find users by tenant and username pattern.
     * Useful for search/autocomplete.
     *
     * @param tenantId Tenant ID
     * @param usernamePattern Username pattern (e.g., "john%")
     * @return List of users matching pattern
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.username LIKE :usernamePattern")
    List<User> findByTenantIdAndUsernamePattern(
            @Param("tenantId") Long tenantId,
            @Param("usernamePattern") String usernamePattern);

    /**
     * Find users who haven't logged in since a certain date.
     * Useful for identifying inactive users.
     *
     * @param tenantId Tenant ID
     * @param since Timestamp to check
     * @return List of users with no login since given date
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND (u.lastLoginAt IS NULL OR u.lastLoginAt < :since)")
    List<User> findInactiveUsers(
            @Param("tenantId") Long tenantId,
            @Param("since") Instant since);

    /**
     * Find users created after a certain date.
     * Useful for reporting on new users.
     *
     * @param tenantId Tenant ID
     * @param since Timestamp to check
     * @return List of users created after given date
     */
    @Query("SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.createdAt >= :since")
    List<User> findUsersCreatedSince(
            @Param("tenantId") Long tenantId,
            @Param("since") Instant since);

    // Método para buscar usuarios por tenant
    List<User> findByTenant(Tenant tenant);

    // Método para buscar usuarios habilitados
    List<User> findByEnabled(boolean enabled);

    // Método para buscar por tenant y habilitados
    List<User> findByTenantAndEnabled(Tenant tenant, boolean enabled);

}
