package com.atina.invoice.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a tenant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequest {

    @Size(min = 2, max = 200, message = "Tenant name must be between 2 and 200 characters")
    private String tenantName;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @Pattern(regexp = "^(FREE|BASIC|PREMIUM|UNLIMITED)$", message = "Tier must be: FREE, BASIC, PREMIUM, or UNLIMITED")
    private String subscriptionTier;

    @Min(value = 0, message = "Max API calls must be non-negative")
    private Long maxApiCallsPerMonth;

    @Min(value = 0, message = "Max storage must be non-negative")
    private Long maxStorageMb;

    private Boolean enabled;

    private Boolean extractionEnabled;

    private Boolean webhookEnabled;
}