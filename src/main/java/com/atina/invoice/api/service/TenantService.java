package com.atina.invoice.api.service;

import com.atina.invoice.api.dto.request.CreateTenantRequest;
import com.atina.invoice.api.dto.request.UpdateTenantRequest;
import com.atina.invoice.api.dto.response.TenantResponse;
import com.atina.invoice.api.model.Tenant;
import com.atina.invoice.api.repository.TenantRepository;
import com.atina.invoice.api.repository.UserRepository;
import com.atina.invoice.api.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for tenant management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MetricsRepository metricsRepository;

    /**
     * Create a new tenant
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        log.info("Creating new tenant: {}", request.getTenantCode());

        // Check if tenant code already exists
        if (tenantRepository.existsByTenantCode(request.getTenantCode())) {
            throw new IllegalArgumentException("Tenant code already exists: " + request.getTenantCode());
        }

        // Create tenant
        Tenant tenant = Tenant.builder()
                .tenantCode(request.getTenantCode())
                .tenantName(request.getTenantName())
                .contactEmail(request.getContactEmail())
                .subscriptionTier(request.getSubscriptionTier())
                .maxApiCallsPerMonth(request.getMaxApiCallsPerMonth())
                .maxStorageMb(request.getMaxStorageMb())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .extractionEnabled(request.getExtractionEnabled() != null ? request.getExtractionEnabled() : true)
                .webhookEnabled(request.getWebhookEnabled() != null ? request.getWebhookEnabled() : true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);

        log.info("Tenant created successfully: {} (ID: {})", savedTenant.getTenantCode(), savedTenant.getId());

        return mapToResponse(savedTenant);
    }

    /**
     * Get all tenants
     */
    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        log.debug("Fetching all tenants");

        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get tenant by ID
     */
    @Transactional(readOnly = true)
    public TenantResponse getTenantById(Long id) {
        log.debug("Fetching tenant by ID: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));

        return mapToResponse(tenant);
    }

    /**
     * Get tenant by code
     */
    @Transactional(readOnly = true)
    public TenantResponse getTenantByCode(String code) {
        log.debug("Fetching tenant by code: {}", code);

        Tenant tenant = tenantRepository.findByTenantCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + code));

        return mapToResponse(tenant);
    }

    /**
     * Update tenant
     */
    @Transactional
    public TenantResponse updateTenant(Long id, UpdateTenantRequest request) {
        log.info("Updating tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));

        // Update fields if provided
        if (request.getTenantName() != null) {
            tenant.setTenantName(request.getTenantName());
        }
        if (request.getContactEmail() != null) {
            tenant.setContactEmail(request.getContactEmail());
        }
        if (request.getSubscriptionTier() != null) {
            tenant.setSubscriptionTier(request.getSubscriptionTier());
        }
        if (request.getMaxApiCallsPerMonth() != null) {
            tenant.setMaxApiCallsPerMonth(request.getMaxApiCallsPerMonth());
        }
        if (request.getMaxStorageMb() != null) {
            tenant.setMaxStorageMb(request.getMaxStorageMb());
        }
        if (request.getEnabled() != null) {
            tenant.setEnabled(request.getEnabled());
        }
        if (request.getExtractionEnabled() != null) {
            tenant.setExtractionEnabled(request.getExtractionEnabled());
        }
        if (request.getWebhookEnabled() != null) {
            tenant.setWebhookEnabled(request.getWebhookEnabled());
        }

        tenant.setUpdatedAt(Instant.now());

        Tenant updatedTenant = tenantRepository.save(tenant);

        log.info("Tenant updated successfully: {} (ID: {})", updatedTenant.getTenantCode(), updatedTenant.getId());

        return mapToResponse(updatedTenant);
    }

    /**
     * Delete tenant (soft delete by disabling)
     */
    @Transactional
    public void deleteTenant(Long id) {
        log.info("Deleting tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));

        // Check if tenant has users
        long userCount = userRepository.countByTenantId(id);
        if (userCount > 0) {
            log.warn("Cannot delete tenant {} - has {} users", id, userCount);
            throw new IllegalStateException("Cannot delete tenant with existing users. Disable it instead.");
        }

        // Delete tenant
        tenantRepository.delete(tenant);

        log.info("Tenant deleted successfully: {}", id);
    }

    /**
     * Enable tenant
     */
    @Transactional
    public TenantResponse enableTenant(Long id) {
        log.info("Enabling tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));

        tenant.setEnabled(true);
        tenant.setUpdatedAt(Instant.now());

        Tenant updatedTenant = tenantRepository.save(tenant);

        log.info("Tenant enabled: {}", id);

        return mapToResponse(updatedTenant);
    }

    /**
     * Disable tenant
     */
    @Transactional
    public TenantResponse disableTenant(Long id) {
        log.info("Disabling tenant: {}", id);

        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + id));

        tenant.setEnabled(false);
        tenant.setUpdatedAt(Instant.now());

        Tenant updatedTenant = tenantRepository.save(tenant);

        log.info("Tenant disabled: {}", id);

        return mapToResponse(updatedTenant);
    }

    /**
     * Get tenants by subscription tier
     */
    @Transactional(readOnly = true)
    public List<TenantResponse> getTenantsByTier(String tier) {
        log.debug("Fetching tenants by tier: {}", tier);

        return tenantRepository.findBySubscriptionTier(tier).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Map Tenant entity to TenantResponse DTO
     */
    private TenantResponse mapToResponse(Tenant tenant) {
        // Get additional stats
        Long totalUsers = userRepository.countByTenantId(tenant.getId());
        Long totalApiCalls = metricsRepository.sumMetricValueByTenantIdAndMetricKeyPrefix(
                tenant.getId(), "api.calls.total");

        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .contactEmail(tenant.getContactEmail())
                .subscriptionTier(tenant.getSubscriptionTier())
                .maxApiCallsPerMonth(tenant.getMaxApiCallsPerMonth())
                .maxStorageMb(tenant.getMaxStorageMb())
                .enabled(tenant.isEnabled())
                .extractionEnabled(tenant.isExtractionEnabled())
                .webhookEnabled(tenant.isWebhookEnabled())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .totalUsers(totalUsers)
                .totalApiCalls(totalApiCalls != null ? totalApiCalls : 0L)
                .build();
    }
}
