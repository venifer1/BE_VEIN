package com.vein.market.feargreed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vein.market.provider.alternativeme.AlternativeMeProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Fear&amp;Greed history (sparkline) for {@code GET /market/fear-greed/history}.
 * Reuses {@link AlternativeMeProvider}; never throws on upstream failure —
 * returns an empty series and logs WARN. The provider holds a ~10 min TTL cache.
 */
@Service
@Slf4j
public class FearGreedService {

    private final AlternativeMeProvider provider;

    public FearGreedService(AlternativeMeProvider provider) {
        this.provider = provider;
    }

    /** One sparkline point. {@code date} is the UTC calendar date (yyyy-MM-dd). */
    public record HistoryRow(String date, String value, String classification) {
    }

    /** Ascending-by-date history, empty on upstream failure. */
    public List<HistoryRow> history(int days) {
        List<AlternativeMeProvider.HistoryPoint> points;
        try {
            points = provider.fetchHistory(days);
        } catch (RuntimeException e) {
            log.warn("fear-greed history unavailable: {}", e.getMessage());
            return List.of();
        }
        List<HistoryRow> rows = new ArrayList<>(points.size());
        for (AlternativeMeProvider.HistoryPoint p : points) {
            LocalDate date = Instant.ofEpochSecond(p.timestamp()).atZone(ZoneOffset.UTC).toLocalDate();
            rows.add(new HistoryRow(
                    date.toString(),
                    p.value() == null ? null : p.value().toPlainString(),
                    p.classification()));
        }
        return rows;
    }
}
