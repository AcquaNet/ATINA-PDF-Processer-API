package com.atina.invoice.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tenant Interceptor
 * Extracts tenant information from JWT and sets it in TenantContext
 * Runs on every request AFTER authentication
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract JWT token from Authorization header
        String token = extractTokenFromRequest(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                // Extract tenant information from JWT
                Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                String tenantCode = jwtTokenProvider.getTenantCodeFromToken(token);

                if (tenantId != null) {
                    // Set tenant context
                    TenantContext.setTenantId(tenantId);
                    TenantContext.setTenantCode(tenantCode);

                    log.debug("Tenant context set: tenantId={}, tenantCode={}", tenantId, tenantCode);
                } else {
                    log.warn("JWT token does not contain tenant information");
                }
            } catch (Exception e) {
                log.error("Failed to extract tenant from JWT", e);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        // CRITICAL: Clear tenant context after request completes
        TenantContext.clear();
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
}
