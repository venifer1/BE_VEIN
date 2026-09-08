package com.vein.macro;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.vein.market.provider.equity.EquitySidecarClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 종목별 다음 실적발표일 조회 (기획서 §15.1 후반부). 미국·코스피·코스닥 종목만 대상이며
 * 값은 Python 사이드카(yfinance)에서 온다. 사이드카가 없거나 실패하면 <b>조용히 null</b>로
 * 폴백해 EventRisk가 해당 종목엔 실적 항목을 붙이지 않는다(스텁-폴백 철학).
 *
 * <p>신호 상세는 자주 열리므로 심볼별 6시간 TTL 캐시로 사이드카 호출을 억제한다.
 * 미상(null) 결과도 캐시해 반복 조회 시 사이드카를 다시 때리지 않는다.
 */
@Component
@Slf4j
public class EarningsProvider {

    private static final Set<String> EQUITY = Set.of("US", "KOSPI", "KOSDAQ");
    private static final long TTL_MS = 6 * 60 * 60 * 1000L;

    private final EquitySidecarClient sidecar;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(LocalDate date, long at) {
    }

    public EarningsProvider(EquitySidecarClient sidecar) {
        this.sidecar = sidecar;
    }

    /** 다음 실적발표일(주식만). 미가용/비주식/파싱실패 시 null. 절대 throw하지 않는다. */
    public LocalDate nextEarnings(String market, String symbol) {
        if (market == null || symbol == null || !EQUITY.contains(market)) {
            return null;
        }
        String key = market + ":" + symbol;
        long now = System.currentTimeMillis();
        Cached c = cache.get(key);
        if (c != null && (now - c.at()) < TTL_MS) {
            return c.date();
        }
        LocalDate date = null;
        try {
            String iso = sidecar.fetchNextEarnings(market, symbol);
            if (iso != null && !iso.isBlank()) {
                date = LocalDate.parse(iso.substring(0, Math.min(10, iso.length())));
            }
        } catch (RuntimeException e) {
            log.debug("earnings parse {} {} failed: {}", market, symbol, e.getMessage());
            date = null;
        }
        cache.put(key, new Cached(date, now));
        return date;
    }
}
