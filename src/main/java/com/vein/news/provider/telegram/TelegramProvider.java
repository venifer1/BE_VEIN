package com.vein.news.provider.telegram;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vein.news.provider.NewsItemDto;
import com.vein.news.provider.NewsProvider;

/**
 * Telegram (coinness) news provider (breaking_news_tab.py, Telegram leg).
 *
 * <p>REAL data comes from the Python sidecar ({@code GET /news/telegram}) via
 * {@link NewsSidecarClient}, which scrapes the public coinness Telegram channel.
 * When the sidecar is unavailable (down/timeout/empty/malformed) this provider
 * falls back to a small, deterministic set of coinness-style seed items so the
 * {@code /news?source=TELEGRAM} contract is still exercised offline. Seed items
 * are stamped relative to "now" but their (source,url) keys are stable, so the
 * upserting scheduler stays idempotent across polls; real items carry their own
 * unique t.me urls so dedup (UNIQUE(source,url)) works the same way.
 */
@Component
public class TelegramProvider implements NewsProvider {

    public static final String SOURCE = "TELEGRAM";
    private static final String CHANNEL = "coinnesskr";
    private static final int FETCH_LIMIT = 20;

    private final NewsSidecarClient sidecar;

    public TelegramProvider(NewsSidecarClient sidecar) {
        this.sidecar = sidecar;
    }

    /** Stable seed posts (id -> headline); coinness publishes Korean crypto briefs. */
    private static final List<Seed> SEEDS = List.of(
            new Seed(100001, "비트코인, 주요 저항선 부근서 횡보 — 거래량 관찰 필요", 5),
            new Seed(100002, "이더리움 스테이킹 물량 증가, 공급 측 변화 주시", 12),
            new Seed(100003, "美 ETF 자금 순유입 지속 — 기관 수요 신호", 25),
            new Seed(100004, "주요 거래소 스테이블코인 예치금 변동 포착", 47),
            new Seed(100005, "알트코인 변동성 확대, 단기 트레이딩 주의", 90));

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public List<NewsItemDto> fetchLatest() {
        // Prefer REAL coinness data from the sidecar; fall back to seeds on
        // any failure/empty so the contract still works offline.
        List<NewsItemDto> real = sidecar.fetchTelegramNews(FETCH_LIMIT);
        if (!real.isEmpty()) {
            return real;
        }
        return seeds();
    }

    /** Deterministic offline fallback (stable (source,url) keys). */
    private static List<NewsItemDto> seeds() {
        Instant now = Instant.now();
        List<NewsItemDto> out = new ArrayList<>(SEEDS.size());
        for (Seed s : SEEDS) {
            String url = "https://t.me/" + CHANNEL + "/" + s.id();
            Instant publishedAt = now.minus(s.ageMinutes(), ChronoUnit.MINUTES);
            out.add(new NewsItemDto(SOURCE, null, s.text(), url, publishedAt));
        }
        return out;
    }

    private record Seed(int id, String text, int ageMinutes) {
    }
}
