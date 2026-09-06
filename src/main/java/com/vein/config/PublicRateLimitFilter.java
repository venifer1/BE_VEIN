package com.vein.config;

import java.io.IOException;
import java.util.List;
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
 * In-memory per-IP fixed-window rate limit for the unauthenticated endpoints that
 * would otherwise have none (MONETIZATION 단계2 ②·①: "레이트리밋 필수 — 현재 API
 * 전체에 없다"). Two guarded rules; every other path passes straight through:
 * <ul>
 *   <li>{@code /api/v1/public/**} — 60 req/min (report scraping)</li>
 *   <li>{@code /api/v1/auth/signup} — 10 req/hour (bot signups polluting the DB)</li>
 * </ul>
 *
 * <p>Dependency-free (no bucket4j). On breach it writes a 429 with the standard error
 * envelope directly — filters run before the DispatcherServlet so
 * {@code @RestControllerAdvice} would not see a thrown exception.
 */
@Component
@Order(1)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    /** Bound the IP map so a stream of distinct source IPs can't grow it without limit. */
    private static final int MAX_TRACKED_IPS = 20_000;

    /** One guarded path rule. {@code exact} matches the URI exactly; else it is a prefix. */
    private record Rule(String path, boolean exact, int max, long windowMs, int retryAfterSec) {
        boolean matches(String uri) {
            return exact ? uri.equals(path) : uri.startsWith(path);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("/api/v1/auth/signup", true, 10, 3_600_000L, 3600),
            new Rule("/api/v1/public/", false, 60, 60_000L, 60));

    /** Key: "rulePath|clientIp". */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        Rule rule = ruleFor(uri);
        if (rule == null) {
            chain.doFilter(req, res);
            return;
        }
        long now = System.currentTimeMillis();
        if (allow(rule.path() + "|" + clientIp(req), rule, now)) {
            chain.doFilter(req, res);
        } else {
            res.setHeader("Retry-After", Integer.toString(rule.retryAfterSec()));
            ApiError.write(res, ErrorCode.RATE_LIMITED.status().value(), ErrorCode.RATE_LIMITED.name());
        }
    }

    private static Rule ruleFor(String uri) {
        if (uri == null) {
            return null;
        }
        for (Rule r : RULES) {
            if (r.matches(uri)) {
                return r;
            }
        }
        return null;
    }

    private boolean allow(String key, Rule rule, long now) {
        if (windows.size() > MAX_TRACKED_IPS) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMs >= e.getValue().windowMs);
        }
        Window w = windows.compute(key, (k, cur) ->
                (cur == null || now - cur.startMs >= rule.windowMs()) ? new Window(now, rule.windowMs()) : cur);
        return w.count.incrementAndGet() <= rule.max();
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
        final long windowMs;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMs, long windowMs) {
            this.startMs = startMs;
            this.windowMs = windowMs;
        }
    }
}
