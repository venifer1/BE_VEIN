package com.vein.common;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * UTC time helpers. All business times are UTC/ISO-8601 with {@code Z} suffix.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /** ISO-8601 UTC with Z suffix, e.g. {@code 2026-06-12T15:00:00Z}. */
    public static String toIso(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    public static Instant parseIso(String iso) {
        return iso == null ? null : Instant.parse(iso);
    }

    /**
     * Freshness per 표 13 / §10.2: DELAYED when elapsed since collectedAt
     * exceeds 2 × timeframe duration; otherwise FRESH.
     */
    public static Freshness freshness(Instant collectedAt, Timeframe tf, Instant now) {
        if (collectedAt == null) {
            return Freshness.DELAYED;
        }
        Duration elapsed = Duration.between(collectedAt, now);
        Duration limit = tf.duration().multipliedBy(2);
        return elapsed.compareTo(limit) > 0 ? Freshness.DELAYED : Freshness.FRESH;
    }
}
