package com.groupcc2.recruitment.common.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ApiErrorFactory.CORRELATION_ID_ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            int status = response.getStatus();
            if (status >= 500) {
                log.error("http_request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else if (status >= 400) {
                log.warn("http_request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else {
                log.info("http_request method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
