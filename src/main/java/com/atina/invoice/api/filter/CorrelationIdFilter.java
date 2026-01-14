package com.atina.invoice.api.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to generate and inject correlationId for ALL requests
 * Runs BEFORE security filters to ensure auth endpoints also get correlationId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // Run FIRST - before security
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. Get correlationId from header (if client sent one)
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);

            // 2. Generate new one if not present
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            // 3. Store in MDC (for logging and controllers)
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // 4. Add to response header (so client can track)
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            // 5. Continue filter chain
            chain.doFilter(request, response);

        } finally {
            // 6. Clean up MDC (important!)
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
