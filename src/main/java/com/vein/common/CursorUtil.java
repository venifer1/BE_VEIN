package com.vein.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Cursor pagination: base64 of {@code detected_at|id} (newest-first lists).
 */
public final class CursorUtil {

    private CursorUtil() {
    }

    public static String encode(Instant ts, long id) {
        String raw = ts.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record Decoded(Instant ts, long id) {
    }

    public static Decoded decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            return new Decoded(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_CURSOR, "Invalid cursor");
        }
    }
}
