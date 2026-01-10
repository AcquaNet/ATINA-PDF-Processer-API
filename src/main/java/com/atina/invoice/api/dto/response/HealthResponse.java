package com.atina.invoice.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Health check response")
public class HealthResponse {

    @Schema(description = "Overall status", required = true, example = "UP")
    private String status;

    @Schema(description = "Timestamp of health check")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "Component health statuses")
    private Map<String, ComponentHealth> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String status;
        private String version;
        private Map<String, Object> details;
        private String error;
    }
}