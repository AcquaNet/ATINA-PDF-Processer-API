package com.atina.invoice.api.config;

import com.atina.invoice.api.security.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * Registers the TenantInterceptor to extract tenant from JWT on every request
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/api/**")  // Apply to all API endpoints
                .excludePathPatterns(
                        "/api/v1/auth/**",      // Exclude auth endpoints (before login)
                        "/api/v1/health",       // Exclude health check
                        "/api/v1/info"          // Exclude info endpoint
                );
    }
}
