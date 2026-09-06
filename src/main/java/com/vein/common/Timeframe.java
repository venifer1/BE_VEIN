package com.vein.common;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported timeframes across markets.
 * <ul>
 *   <li>CRYPTO: {@code 15m | 1h | 4h | 1d | 3d | 1w | 1M}</li>
 *   <li>US / KOSPI / KOSDAQ (equities): {@code 1d | 3d | 1w}</li>
 * </ul>
 * The server returns the per-market support set; {@link #supportedFor(String)}
 * is the single source of truth for valid (market, timeframe) combos.
 */
public enum Timeframe {
    M15("15m", Duration.ofMinutes(15)),
    H1("1h", Duration.ofHours(1)),
    H4("4h", Duration.ofHours(4)),
    D1("1d", Duration.ofDays(1)),
    D3("3d", Duration.ofDays(3)),
    W1("1w", Duration.ofDays(7)),
    MN1("1M", Duration.ofDays(30));

    /** Crypto supports the full intraday + multi-day set. */
    private static final Set<Timeframe> CRYPTO = Set.of(M15, H1, H4, D1, D3, W1, MN1);
    /** Equities (US/KR) only support day/3-day/week. */
    private static final Set<Timeframe> EQUITY = Set.of(D1, D3, W1);

    private final String code;
    private final Duration duration;

    Timeframe(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }

    @JsonCreator
    public static Timeframe fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_TIMEFRAME,
                        "Unsupported timeframe: " + code));
    }

    /** Supported timeframe set for a market code (CRYPTO/US/KOSPI/KOSDAQ). */
    public static Set<Timeframe> supportedFor(String market) {
        if (market == null) {
            return CRYPTO;
        }
        return switch (market.toUpperCase()) {
            case "CRYPTO" -> CRYPTO;
            case "US", "KOSPI", "KOSDAQ" -> EQUITY;
            default -> CRYPTO;
        };
    }

    /** Whether this timeframe is valid for the given market. */
    public boolean isSupportedFor(String market) {
        return supportedFor(market).contains(this);
    }
}
