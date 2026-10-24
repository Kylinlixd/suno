package com.suno.mall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * M4 请求观测：为每个请求生成 X-Request-Id（响应回传 + MDC 供日志关联），
 * 慢请求（>slowThresholdMs）输出 WARN 日志，便于链路排查与容量评估。
 */
@Component
@Order(0)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    private final long slowThresholdMs;

    public RequestObservabilityFilter(@org.springframework.beans.factory.annotation.Value("${mall.observability.slow-request-ms:1000}") long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long start = System.currentTimeMillis();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > slowThresholdMs) {
                log.warn("slow request id={} {} {} status={} cost={}ms", requestId,
                        request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
            }
            MDC.remove("requestId");
        }
    }
}
