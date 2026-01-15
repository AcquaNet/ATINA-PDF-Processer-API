package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for tenant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("tenantCode")
    private String tenantCode;

    @JsonProperty("tenantName")
    private String tenantName;

    @JsonProperty("contactEmail")
    private String contactEmail;

    @JsonProperty("subscriptionTier")
    private String subscriptionTier;

    @JsonProperty("maxApiCallsPerMonth")
    private Long maxApiCallsPerMonth;

    @JsonProperty("maxStorageMb")
    private Long maxStorageMb;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("totalUsers")
    private Long totalUsers;

    @JsonProperty("totalApiCalls")
    private Long totalApiCalls;
}
