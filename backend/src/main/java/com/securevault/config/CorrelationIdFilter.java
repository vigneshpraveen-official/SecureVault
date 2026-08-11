package com.securevault.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One correlation id per request, in the MDC (so every log line for this request carries it — see
 * logback-spring.xml's pattern) and echoed back as a response header, so a client can quote it when
 * reporting a problem and it's traceable straight back to the log lines (P4.7/M-47). Honors an
 * incoming X-Correlation-Id if the caller already has one (e.g. a gateway upstream), otherwise
 * generates one. Runs first in the chain — before JwtAuthenticationFilter — so even a request that
 * never authenticates still gets a correlation id on its 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear — this thread returns to Tomcat's pool and must not leak the previous
            // request's correlation id into whatever it handles next.
            MDC.remove(MDC_KEY);
        }
    }
}
