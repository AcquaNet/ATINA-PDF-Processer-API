// ============================================
// FILE: dto/request/CreateTenantRequest.java
// ============================================

package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    @NotBlank(message = "Tenant code is required")
    @Size(min = 2, max = 50, message = "Tenant code must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Tenant code must contain only uppercase letters, numbers, hyphens and underscores")
    private String tenantCode;

    @NotBlank(message = "Tenant name is required")
    @Size(min = 2, max = 200, message = "Tenant name must be between 2 and 200 characters")
    private String tenantName;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Subscription tier is required")
    @Pattern(regexp = "^(FREE|BASIC|PREMIUM|UNLIMITED)$", message = "Tier must be: FREE, BASIC, PREMIUM, or UNLIMITED")
    private String subscriptionTier;

    @Min(value = 0, message = "Max API calls must be non-negative")
    private Long maxApiCallsPerMonth;

    @Min(value = 0, message = "Max storage must be non-negative")
    private Long maxStorageMb;

    private Boolean enabled = true;

    private Boolean extractionEnabled = true;

    private Boolean webhookEnabled = true;
}
