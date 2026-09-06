package com.vein.news.provider;

import java.util.List;

/** A source of breaking-news items (Bloomberg RSS, Telegram coinness, ...). */
public interface NewsProvider {

    /** Source code stored in {@code news_items.source} (TELEGRAM | BLOOMBERG). */
    String source();

    /** Fetch the most recent items, newest-first. Never returns null. */
    List<NewsItemDto> fetchLatest();
}
