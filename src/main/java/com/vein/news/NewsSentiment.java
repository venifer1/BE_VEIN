package com.vein.news;

/**
 * Keyword-derived sentiment of a news item (see {@link NewsClassifier}).
 * Serialized as its name in the GET /news response.
 */
public enum NewsSentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}
