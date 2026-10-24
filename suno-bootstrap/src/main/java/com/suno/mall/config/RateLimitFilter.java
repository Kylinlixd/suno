package com.suno.mall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M4 固定窗口限流：按「客户端 IP + 路径」计数，窗口期内超过配额返回 429 + RATE_LIMITED。
 * 覆盖登录/注册/下单/支付/回调等高风险写接口；无 Redis 环境单机生效，
 * 多实例部署时应在网关叠加全局限流（见配置注释）。
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final long limit;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final java.util.Set<String> PROTECTED_PATHS = java.util.Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/mall/orders",
            "/api/mall/orders/pay",
            "/api/payment/callback",
            "/api/payment/alipay/notify"
    );

    public RateLimitFilter(@Value("${mall.rate-limit.enabled:true}") boolean enabled,
                           @Value("${mall.rate-limit.limit:60}") long limit,
                           @Value("${mall.rate-limit.window-ms:60000}") long windowMillis) {
        this.enabled = enabled;
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !PROTECTED_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String key = clientIp(request) + "|" + request.getRequestURI();
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });
        long count = window.count.incrementAndGet();
        if (count > limit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"操作过于频繁，请稍后再试\",\"errorCode\":\"RATE_LIMITED\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long start;
        final AtomicLong count = new AtomicLong();

        Window(long start) {
            this.start = start;
        }
    }
}
