package com.vein.news.provider;

import java.time.Instant;

/**
 * Normalized news item emitted by a {@code NewsProvider} (Bloomberg / Telegram).
 * Provider-internal DTOs (RSS XML records) never leave the provider package —
 * everything is normalized to this shape before reaching the service/DB.
 *
 * @param source       TELEGRAM | BLOOMBERG
 * @param title        headline (may be null for body-only Telegram posts)
 * @param body         summary / message body
 * @param url          canonical link (always non-null; synthetic if upstream lacks one)
 * @param publishedAt  upstream publish time (UTC), or null if unknown
 */
public record NewsItemDto(String source, String title, String body, String url, Instant publishedAt) {
}
