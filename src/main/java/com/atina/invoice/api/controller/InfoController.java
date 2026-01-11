package com.atina.invoice.api.controller;

import com.atina.invoice.api.dto.response.ApiResponse;
import com.atina.invoice.api.dto.response.InfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Info controller
 * Provides API and application information
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Info", description = "API information endpoints")
public class InfoController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.name:Invoice Extractor API}")
    private String appName;

    @Value("${app.description:AI-powered invoice data extraction engine}")
    private String appDescription;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Get API information
     *
     * GET /api/v1/info
     *
     * Returns information about the API, engine, and build
     */
    @GetMapping("/info")
    @Operation(
            summary = "Get API information",
            description = "Returns information about API version, engine capabilities, and build details"
    )
    public ApiResponse<InfoResponse> getInfo() {
        log.debug("API info requested");

        // Application info
        InfoResponse.Application application = InfoResponse.Application.builder()
                .name(appName)
                .version(appVersion)
                .description(appDescription)
                .environment(activeProfile)  // FIXED: usar environment en lugar de profile
                .build();

        // Build info
        InfoResponse.Build build = InfoResponse.Build.builder()
                .timestamp(Instant.now().toString())
                .commit("N/A")
                .branch("main")
                .build();

        // Engine info
        InfoResponse.Engine engine = InfoResponse.Engine.builder()
                .version("1.0.0")
                .supportedRuleTypes(getSupportedRuleTypes())
                .build();

        // API info
        InfoResponse.Api api = InfoResponse.Api.builder()
                .version("v1")
                .documentation("/swagger-ui.html")
                .build();

        // Build response
        InfoResponse response = InfoResponse.builder()
                .application(application)
                .build(build)
                .engine(engine)
                .api(api)
                .build();

        return ApiResponse.success(response, MDC.get("correlationId"), 0L);
    }

    /**
     * Get supported rule types
     */
    private List<String> getSupportedRuleTypes() {
        return Arrays.asList(
                "anchor_proximity",
                "region_anchor_proximity",
                "line_regex",
                "global_regex",
                "table_by_headers"
        );
    }
}
