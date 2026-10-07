package com.example.loop_assessment.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader("X-Request-ID");

        if (requestId == null || !requestId.matches("[a-zA-Z0-9-]{1,64}"))
            requestId = UUID.randomUUID().toString();

        long start = System.nanoTime();
        try (var ignored = MDC.putCloseable("requestId", requestId)) {
            response.setHeader("X-Request-ID", requestId);

            try {
                chain.doFilter(request, response);
            } finally {
                log.atInfo()
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                        .log("HTTP request completed");
            }
        }
    }
}
