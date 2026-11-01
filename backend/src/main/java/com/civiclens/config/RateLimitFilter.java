package com.civiclens.config;

import com.civiclens.common.ErrorCode;
import com.civiclens.common.ErrorResponseWriter;
import com.civiclens.user.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window abuse control. Login/registration are limited per client IP; other writes per user (or IP when
 * anonymous). Counters are in memory, so each instance counts separately; see SECURITY.md.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties.RateLimit config;
    private final ErrorResponseWriter writer;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props, ErrorResponseWriter writer, Clock clock) {
        this.config = props.rateLimit();
        this.writer = writer;
        this.clock = clock;
    }

    private record Window(long start, AtomicInteger count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (config == null || !config.enabled()) {
            return true;
        }
        String method = request.getMethod();
        return !request.getRequestURI().startsWith("/api/") || "GET".equals(method) || "OPTIONS".equals(method)
                || "HEAD".equals(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authEndpoint = request.getRequestURI().startsWith("/api/v1/auth/login")
                || request.getRequestURI().startsWith("/api/v1/auth/register");
        String key;
        int limit;
        if (authEndpoint) {
            key = "auth:" + request.getRemoteAddr();
            limit = config.authRequestsPerWindow();
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            key = auth != null && auth.getPrincipal() instanceof CurrentUser u
                    ? "user:" + u.id() : "ip:" + request.getRemoteAddr();
            limit = config.writeRequestsPerWindow();
        }
        long windowMillis = (config.window() == null ? Duration.ofMinutes(1) : config.window()).toMillis();
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);
        Window w = windows.compute(key, (k, existing) ->
                existing == null || existing.start() != windowStart ? new Window(windowStart, new AtomicInteger()) : existing);
        if (w.count().incrementAndGet() > limit) {
            long retryAfter = Math.max(1, (windowStart + windowMillis - now + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            writer.write(response, ErrorCode.RATE_LIMITED, "Too many requests. Retry after " + retryAfter + " seconds.");
            return;
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> e.getValue().start() != windowStart);
        }
        chain.doFilter(request, response);
    }

    /** Test hook. */
    public void reset() {
        windows.clear();
    }
}
