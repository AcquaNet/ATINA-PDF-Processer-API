package com.atina.invoice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO para respuesta de usuario (sin password)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Boolean enabled;
    private Instant createdAt;
    private Instant lastLoginAt;

    // Información del tenant
    private TenantInfo tenant;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo {
        private Long id;
        private String tenantCode;
        private String tenantName;
    }
}
