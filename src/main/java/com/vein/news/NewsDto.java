package com.vein.news;

import java.util.List;

/**
 * Public news item (GET /news). {@code isNew} = collected within the last poll
 * window (server marks freshly ingested items so the app can badge them).
 *
 * <p>{@code sentiment} (POSITIVE/NEGATIVE/NEUTRAL) and {@code taggedSymbols} (e.g.
 * {@code ["KRW-BTC"]}) are computed on read by {@link NewsClassifier} from the
 * title+body; they are not persisted. Serialized as {@code tagged_symbols}.
 *
 * <p>{@code taggedInstruments} mirrors {@code taggedSymbols} but pairs each symbol
 * with its CRYPTO instrument id (null when unresolved), enabling the app to deep-link
 * a chip to {@code /instruments/{id}}. Serialized as {@code tagged_instruments}
 * (each entry: {@code {"symbol": "KRW-BTC", "instrument_id": 1}}).
 */
public record NewsDto(Long id, String source, String title, String body, String url,
                      String publishedAt, boolean isNew,
                      NewsSentiment sentiment, List<String> taggedSymbols,
                      List<TaggedInstrument> taggedInstruments) {

    /** A tagged symbol resolved to its instrument id ({@code instrumentId} null if unresolved). */
    public record TaggedInstrument(String symbol, Long instrumentId) {
    }
}
