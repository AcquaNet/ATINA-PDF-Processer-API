package com.atina.invoice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Login Response - Enhanced with tenant information
 * Returns JWT token and tenant details to the client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT token
     */
    @JsonProperty("token")
    private String token;

    /**
     * Token expiration time
     */
    @JsonProperty("expiresAt")
    private Instant expiresAt;

    /**
     * Username
     */
    @JsonProperty("username")
    private String username;

    /**
     * User's full name
     */
    @JsonProperty("fullName")
    private String fullName;

    /**
     * User's email
     */
    @JsonProperty("email")
    private String email;

    /**
     * User's role within their tenant
     */
    @JsonProperty("role")
    private String role;

    /**
     * Tenant information
     */
    @JsonProperty("tenant")
    private TenantInfo tenant;

    /**
     * Nested class for tenant information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("code")
        private String code;

        @JsonProperty("name")
        private String name;

        @JsonProperty("subscriptionTier")
        private String subscriptionTier;

        @JsonProperty("maxApiCallsPerMonth")
        private Long maxApiCallsPerMonth;

        @JsonProperty("enabled")
        private boolean enabled;
    }
}
