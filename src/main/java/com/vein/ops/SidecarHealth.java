package com.vein.ops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.vein.market.provider.equity.EquitySidecarClient;

/**
 * Cached view of Python sidecar health, used to tell REAL sidecar-backed data
 * apart from the SYNTHETIC stub fallback (PROJECT_STATUS §6 최우선 함정: 사이드카가
 * 죽으면 에러 없이 합성 스텁으로 폴백 → 가짜 데이터를 진짜로 착각).
 *
 * <p>{@link EquitySidecarClient#isHealthy()} does an HTTP round-trip, so this
 * caches the result for {@link #TTL_MS} to keep the frequently-polled
 * {@code /system/status} cheap. Never throws — degrades to "unhealthy".
 */
@Component
public class SidecarHealth {

    private static final long TTL_MS = 30_000L;

    private final EquitySidecarClient client;
    private final String url;

    private volatile boolean healthy;
    private volatile long checkedAt;

    public SidecarHealth(EquitySidecarClient client,
                         @Value("${vein.sidecar.base-url:http://localhost:8099}") String url) {
        this.client = client;
        this.url = url;
    }

    /** True if the sidecar answered a health check within the last {@link #TTL_MS}. */
    public boolean healthy() {
        long now = System.currentTimeMillis();
        if (now - checkedAt >= TTL_MS) {
            boolean ok;
            try {
                ok = client.isHealthy();
            } catch (RuntimeException e) {
                ok = false;
            }
            healthy = ok;
            checkedAt = now;
        }
        return healthy;
    }

    public String url() {
        return url;
    }
}
