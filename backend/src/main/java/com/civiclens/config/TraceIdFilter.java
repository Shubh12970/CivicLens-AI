package com.civiclens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns each request a trace id (accepting a safe client-supplied {@code X-Request-Id}), puts it in the logging
 * MDC and echoes it in the {@code X-Trace-Id} response header and in every error body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Logger log = LoggerFactory.getLogger("com.civiclens.http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader("X-Request-Id");
        String traceId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")) {
                // Path only: never log query strings, headers or bodies (they can carry tokens or personal data).
                log.info("http method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
