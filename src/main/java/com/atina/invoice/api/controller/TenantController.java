package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.request.CreateTenantRequest;
import com.atina.invoice.api.dto.request.UpdateTenantRequest;
import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.TenantResponse;
import com.atina.invoice.api.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for tenant management
 * Only accessible by SYSTEM_ADMIN
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Management", description = "Endpoints for managing tenants (SYSTEM_ADMIN only)")
public class TenantController {

    private final TenantService tenantService;

    /**
     * Create a new tenant
     *
     * POST /api/v1/admin/tenants
     */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Create new tenant",
            description = "Create a new tenant in the system. Requires SYSTEM_ADMIN role."
    )
    public ApiResponse<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        log.info("Creating tenant: {}", request.getTenantCode());

        long start = System.currentTimeMillis();

        try {
            TenantResponse response = tenantService.createTenant(request);
            long duration = System.currentTimeMillis() - start;

            log.info("Tenant created successfully: {} ({}ms)", response.getTenantCode(), duration);
            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (IllegalArgumentException e) {
            log.error("Failed to create tenant: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get all tenants
     *
     * GET /api/v1/admin/tenants
     */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get all tenants",
            description = "Retrieve list of all tenants in the system"
    )
    public ApiResponse<List<TenantResponse>> getAllTenants() {
        log.info("Fetching all tenants");

        long start = System.currentTimeMillis();
        List<TenantResponse> tenants = tenantService.getAllTenants();
        long duration = System.currentTimeMillis() - start;

        log.info("Retrieved {} tenants ({}ms)", tenants.size(), duration);
        return ApiResponse.success(tenants, MDC.get("correlationId"), duration);
    }

    /**
     * Get tenant by ID
     *
     * GET /api/v1/admin/tenants/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get tenant by ID",
            description = "Retrieve detailed information about a specific tenant"
    )
    public ApiResponse<TenantResponse> getTenantById(@PathVariable Long id) {
        log.info("Fetching tenant by ID: {}", id);

        long start = System.currentTimeMillis();
        TenantResponse tenant = tenantService.getTenantById(id);
        long duration = System.currentTimeMillis() - start;

        log.info("Retrieved tenant: {} ({}ms)", tenant.getTenantCode(), duration);
        return ApiResponse.success(tenant, MDC.get("correlationId"), duration);
    }

    /**
     * Get tenant by code
     *
     * GET /api/v1/admin/tenants/code/{code}
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get tenant by code",
            description = "Retrieve tenant information by tenant code"
    )
    public ApiResponse<TenantResponse> getTenantByCode(@PathVariable String code) {
        log.info("Fetching tenant by code: {}", code);

        long start = System.currentTimeMillis();
        TenantResponse tenant = tenantService.getTenantByCode(code);
        long duration = System.currentTimeMillis() - start;

        log.info("Retrieved tenant: {} ({}ms)", tenant.getTenantCode(), duration);
        return ApiResponse.success(tenant, MDC.get("correlationId"), duration);
    }

    /**
     * Update tenant
     *
     * PUT /api/v1/admin/tenants/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Update tenant",
            description = "Update tenant information. Only provided fields will be updated."
    )
    public ApiResponse<TenantResponse> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request) {

        log.info("Updating tenant: {}", id);

        long start = System.currentTimeMillis();

        try {
            TenantResponse response = tenantService.updateTenant(id, request);
            long duration = System.currentTimeMillis() - start;

            log.info("Tenant updated successfully: {} ({}ms)", response.getTenantCode(), duration);
            return ApiResponse.success(response, MDC.get("correlationId"), duration);

        } catch (IllegalArgumentException e) {
            log.error("Failed to update tenant {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * Delete tenant
     *
     * DELETE /api/v1/admin/tenants/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Delete tenant",
            description = "Delete a tenant. Only possible if tenant has no users."
    )
    public ApiResponse<String> deleteTenant(@PathVariable Long id) {
        log.info("Deleting tenant: {}", id);

        long start = System.currentTimeMillis();

        try {
            tenantService.deleteTenant(id);
            long duration = System.currentTimeMillis() - start;

            log.info("Tenant deleted successfully: {} ({}ms)", id, duration);
            return ApiResponse.success("Tenant deleted successfully", MDC.get("correlationId"), duration);

        } catch (IllegalStateException e) {
            log.error("Cannot delete tenant {}: {}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * Enable tenant
     *
     * PATCH /api/v1/admin/tenants/{id}/enable
     */
    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Enable tenant",
            description = "Enable a disabled tenant"
    )
    public ApiResponse<TenantResponse> enableTenant(@PathVariable Long id) {
        log.info("Enabling tenant: {}", id);

        long start = System.currentTimeMillis();
        TenantResponse response = tenantService.enableTenant(id);
        long duration = System.currentTimeMillis() - start;

        log.info("Tenant enabled: {} ({}ms)", id, duration);
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }

    /**
     * Disable tenant
     *
     * PATCH /api/v1/admin/tenants/{id}/disable
     */
    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Disable tenant",
            description = "Disable a tenant (prevents all users from this tenant from accessing the system)"
    )
    public ApiResponse<TenantResponse> disableTenant(@PathVariable Long id) {
        log.info("Disabling tenant: {}", id);

        long start = System.currentTimeMillis();
        TenantResponse response = tenantService.disableTenant(id);
        long duration = System.currentTimeMillis() - start;

        log.info("Tenant disabled: {} ({}ms)", id, duration);
        return ApiResponse.success(response, MDC.get("correlationId"), duration);
    }

    /**
     * Get tenants by subscription tier
     *
     * GET /api/v1/admin/tenants/tier/{tier}
     */
    @GetMapping("/tier/{tier}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get tenants by tier",
            description = "Retrieve all tenants with specific subscription tier (FREE, BASIC, PREMIUM, UNLIMITED)"
    )
    public ApiResponse<List<TenantResponse>> getTenantsByTier(@PathVariable String tier) {
        log.info("Fetching tenants by tier: {}", tier);

        long start = System.currentTimeMillis();
        List<TenantResponse> tenants = tenantService.getTenantsByTier(tier);
        long duration = System.currentTimeMillis() - start;

        log.info("Retrieved {} tenants with tier {} ({}ms)", tenants.size(), tier, duration);
        return ApiResponse.success(tenants, MDC.get("correlationId"), duration);
    }
}
