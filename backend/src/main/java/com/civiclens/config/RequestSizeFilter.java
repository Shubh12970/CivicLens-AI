package com.civiclens.config;

import com.civiclens.common.ErrorCode;
import com.civiclens.common.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects request bodies above 64 KB. Every JSON payload in this API is far smaller. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {

    public static final long MAX_BODY_BYTES = 64 * 1024;
    private final ErrorResponseWriter writer;

    public RequestSizeFilter(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (length > MAX_BODY_BYTES || (chunked && !"GET".equals(request.getMethod()))) {
            writer.write(response, ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request body must be at most 64 KB and sent with a Content-Length header.");
            return;
        }
        chain.doFilter(request, response);
    }
}
