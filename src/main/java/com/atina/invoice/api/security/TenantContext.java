package com.atina.invoice.api.security;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local context for storing current tenant information
 * Allows any service to access the current tenant without passing it explicitly
 */
@Slf4j
public class TenantContext {

    private static final ThreadLocal<Long> currentTenantId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTenantCode = new ThreadLocal<>();

    /**
     * Set the current tenant for this thread
     */
    public static void setTenantId(Long tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        currentTenantId.set(tenantId);
    }

    /**
     * Set the current tenant code for this thread
     */
    public static void setTenantCode(String tenantCode) {
        log.debug("Setting tenant code context: {}", tenantCode);
        currentTenantCode.set(tenantCode);
    }

    /**
     * Get the current tenant ID
     */
    public static Long getTenantId() {
        return currentTenantId.get();
    }

    /**
     * Get the current tenant code
     */
    public static String getTenantCode() {
        return currentTenantCode.get();
    }

    /**
     * Check if tenant context is set
     */
    public static boolean isSet() {
        return currentTenantId.get() != null;
    }

    /**
     * Clear tenant context (important: call after request completes)
     */
    public static void clear() {
        log.debug("Clearing tenant context");
        currentTenantId.remove();
        currentTenantCode.remove();
    }

    /**
     * Get current tenant ID or throw exception if not set
     */
    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available");
        }
        return tenantId;
    }
}
