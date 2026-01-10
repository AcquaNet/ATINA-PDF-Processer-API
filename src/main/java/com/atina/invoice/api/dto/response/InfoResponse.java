package com.atina.invoice.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Application information")
public class InfoResponse {

    @Schema(description = "Application details")
    private Application application;

    @Schema(description = "Build information")
    private Build build;

    @Schema(description = "Engine details")
    private Engine engine;

    @Schema(description = "API information")
    private Api api;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Application {
        private String name;
        private String version;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Build {
        private String timestamp;
        private String commit;
        private String branch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Engine {
        private String version;
        private List<String> supportedRuleTypes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        private String version;
        private String documentation;
    }
}