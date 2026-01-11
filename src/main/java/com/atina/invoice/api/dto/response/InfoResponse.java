package com.atina.invoice.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for API info endpoint
 */
@Data
@Builder
public class InfoResponse {
    private Application application;
    private Build build;
    private Engine engine;
    private Api api;

    /**
     * Application information
     */
    @Data
    @Builder
    public static class Application {
        private String name;
        private String version;
        private String description;
        private String environment;  // FIXED: cambiar de 'profile' a 'environment'
    }

    /**
     * Build information
     */
    @Data
    @Builder
    public static class Build {
        private String timestamp;
        private String commit;
        private String branch;
    }

    /**
     * Engine information
     */
    @Data
    @Builder
    public static class Engine {
        private String version;
        private List<String> supportedRuleTypes;
    }

    /**
     * API information
     */
    @Data
    @Builder
    public static class Api {
        private String version;
        private String documentation;
    }
}
