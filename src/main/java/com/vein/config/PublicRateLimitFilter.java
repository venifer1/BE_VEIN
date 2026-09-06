package com.vein.config;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vein.common.ApiError;
import com.vein.common.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * In-memory per-IP fixed-window rate limit for the public content endpoints
 * (MONETIZATION 단계2 ②: "레이트리밋 필수 — 현재 API 전체에 없다"). Only guards
 * {@code /api/v1/public/**}; every other path passes straight through.
 *
 * <p>Dependency-free (no bucket4j): a 60 req/min fixed window per client IP is
 * enough to stop bot scraping of the open report. On breach it writes a 429 with
 * the standard error envelope directly — filters run before the DispatcherServlet
 * so {@code @RestControllerAdvice} would not see the exception.
 */
@Component
@Order(1)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/v1/public/";
    private static final int MAX_PER_WINDOW = 60;
    private static final long WINDOW_MS = 60_000L;
    /** Bound the IP map so a stream of distinct source IPs can't grow it without limit. */
    private static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (uri == null || !uri.startsWith(PREFIX)) {
            chain.doFilter(req, res);
            return;
        }
        if (allow(clientIp(req), System.currentTimeMillis())) {
            chain.doFilter(req, res);
        } else {
            res.setHeader("Retry-After", "60");
            ApiError.write(res, ErrorCode.RATE_LIMITED.status().value(), ErrorCode.RATE_LIMITED.name());
        }
    }

    private boolean allow(String key, long now) {
        if (windows.size() > MAX_TRACKED_IPS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMs >= WINDOW_MS);
        }
        Window w = windows.compute(key, (k, cur) ->
                (cur == null || now - cur.startMs >= WINDOW_MS) ? new Window(now) : cur);
        return w.count.incrementAndGet() <= MAX_PER_WINDOW;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
