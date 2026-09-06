package com.vein.news;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.CursorUtil;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.news.provider.NewsItemDto;
import com.vein.news.provider.NewsProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Collects breaking-news from all {@link NewsProvider}s and serves the paged feed
 * (GET /news). Upserts are idempotent on (source, url). An item is flagged
 * {@code is_new} when its collected_at falls inside the last poll window.
 */
@Service
@Slf4j
public class NewsService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** Recent window scanned when filtering by symbol (tags are computed-on-read). */
    private static final int SYMBOL_SCAN_WINDOW = 200;
    /** Items collected within this window of the latest collection are "new". */
    private static final Duration NEW_WINDOW = Duration.ofSeconds(20);
    /** CRYPTO symbol/name index TTL for sentiment tagging. */
    private static final Duration TAG_INDEX_TTL = Duration.ofMinutes(10);

    private final List<NewsProvider> providers;
    private final NewsItemRepository repository;
    private final InstrumentRepository instrumentRepository;

    /** Lazily-built, time-boxed alias(lowercased) -> symbol index for tagging. */
    private volatile Map<String, String> tagIndex = Map.of();
    /** Companion symbol -> CRYPTO instrument id index, rebuilt with {@link #tagIndex}. */
    private volatile Map<String, Long> symbolIdIndex = Map.of();
    private volatile Instant tagIndexExpiry = Instant.EPOCH;

    public NewsService(List<NewsProvider> providers, NewsItemRepository repository,
                       InstrumentRepository instrumentRepository) {
        this.providers = providers;
        this.repository = repository;
        this.instrumentRepository = instrumentRepository;
    }

    public record Page(List<NewsDto> items, String nextCursor) {
    }

    /**
     * Newest-first page of items, optionally filtered by source and/or symbol.
     * When {@code symbol} is present, items are filtered to those whose
     * computed tags contain that symbol (see {@link #listBySymbol}).
     */
    @Transactional(readOnly = true)
    public Page list(String source, String symbol, String cursor, Integer pageSize) {
        String normalizedSymbol = normalizeSymbol(symbol);
        if (normalizedSymbol != null) {
            return listBySymbol(source, normalizedSymbol, cursor, pageSize);
        }

        String normalizedSource = normalizeSource(source);
        int size = clampSize(pageSize);

        Instant cursorTs = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorUtil.Decoded d = CursorUtil.decode(cursor);
            cursorTs = d.ts();
            cursorId = d.id();
        }

        Pageable page = PageRequest.of(0, size + 1);
        List<NewsItem> rows = repository.findPage(normalizedSource, cursorTs, cursorId, page);

        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }

        Instant newThreshold = newThreshold();
        Map<String, String> aliasIndex = tagIndex();
        Map<String, Long> symbolToId = symbolIdIndex;
        List<NewsDto> dtos = new ArrayList<>(rows.size());
        for (NewsItem n : rows) {
            dtos.add(toDto(n, newThreshold, aliasIndex, symbolToId));
        }

        String nextCursor = null;
        if (hasMore && !rows.isEmpty()) {
            NewsItem last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(sortTime(last), last.getId());
        }
        return new Page(dtos, nextCursor);
    }

    /**
     * Symbol-filtered page. Tags are computed-on-read, so we scan a bounded
     * recent window (newest {@value #SYMBOL_SCAN_WINDOW} by published_at),
     * compute tags, keep only items tagged with {@code symbol}, then apply the
     * existing cursor/limit/ordering to that filtered set. Never 500; empty when
     * none match.
     */
    private Page listBySymbol(String source, String symbol, String cursor, Integer pageSize) {
        String normalizedSource = normalizeSource(source);
        int size = clampSize(pageSize);

        Instant cursorTs = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorUtil.Decoded d = CursorUtil.decode(cursor);
            cursorTs = d.ts();
            cursorId = d.id();
        }
        final Instant fCursorTs = cursorTs;
        final Long fCursorId = cursorId;

        Instant newThreshold = newThreshold();
        Map<String, String> aliasIndex = tagIndex();
        Map<String, Long> symbolToId = symbolIdIndex;

        List<NewsItem> window = repository.findRecent(
                normalizedSource, PageRequest.of(0, SYMBOL_SCAN_WINDOW));

        // Compute tags once per row; keep only those tagged with the target symbol.
        // The window is already newest-first, so the filtered list preserves order.
        List<NewsItem> matchedRows = new ArrayList<>();
        List<NewsDto> matched = new ArrayList<>();
        for (NewsItem n : window) {
            NewsDto dto = toDto(n, newThreshold, aliasIndex, symbolToId);
            if (!dto.taggedSymbols().contains(symbol)) {
                continue;
            }
            // Apply the cursor against the same (sortTime, id) ordering as findPage.
            if (fCursorTs != null) {
                Instant ts = sortTime(n);
                boolean after = ts.isBefore(fCursorTs)
                        || (ts.equals(fCursorTs) && fCursorId != null && n.getId() < fCursorId);
                if (!after) {
                    continue;
                }
            }
            matchedRows.add(n);
            matched.add(dto);
        }

        boolean hasMore = matched.size() > size;
        List<NewsDto> pageItems = hasMore ? new ArrayList<>(matched.subList(0, size)) : matched;

        String nextCursor = null;
        if (hasMore && !pageItems.isEmpty()) {
            NewsItem last = matchedRows.get(pageItems.size() - 1);
            nextCursor = CursorUtil.encode(sortTime(last), last.getId());
        }
        return new Page(pageItems, nextCursor);
    }

    /** "new" = collected within the last poll window of the latest collection. */
    private Instant newThreshold() {
        Instant latestCollected = repository.findTopByOrderByCollectedAtDesc()
                .map(NewsItem::getCollectedAt)
                .orElse(null);
        return latestCollected == null ? null : latestCollected.minus(NEW_WINDOW);
    }

    /** Map a row to its public DTO, computing sentiment + tags on read (never throws). */
    private NewsDto toDto(NewsItem n, Instant newThreshold,
                          Map<String, String> aliasIndex, Map<String, Long> symbolToId) {
        boolean isNew = newThreshold != null && n.getCollectedAt() != null
                && !n.getCollectedAt().isBefore(newThreshold);

        // Never 500 on classification: default NEUTRAL / empty tags on any error.
        NewsSentiment sentiment = NewsSentiment.NEUTRAL;
        List<String> tags = List.of();
        try {
            sentiment = NewsClassifier.classify(n.getTitle(), n.getBody());
            tags = NewsClassifier.tag(n.getTitle(), n.getBody(), aliasIndex);
        } catch (RuntimeException e) {
            log.warn("news classify failed for id {}: {}", n.getId(), e.getMessage());
        }

        // Resolve each tagged symbol to its instrument id (null when unresolved).
        List<NewsDto.TaggedInstrument> taggedInstruments = new ArrayList<>(tags.size());
        for (String s : tags) {
            taggedInstruments.add(new NewsDto.TaggedInstrument(s, symbolToId.get(s)));
        }

        return new NewsDto(
                n.getId(),
                n.getSource(),
                n.getTitle(),
                n.getBody(),
                n.getUrl(),
                TimeUtil.toIso(sortTime(n)),
                isNew,
                sentiment,
                tags,
                taggedInstruments);
    }

    /** Most recent collected_at across all rows (system/status freshness). */
    @Transactional(readOnly = true)
    public Instant lastCollectedAt() {
        return repository.findTopByOrderByCollectedAtDesc().map(NewsItem::getCollectedAt).orElse(null);
    }

    @Transactional(readOnly = true)
    public Instant lastCollectedAt(String source) {
        return repository.findTopBySourceOrderByCollectedAtDesc(source)
                .map(NewsItem::getCollectedAt).orElse(null);
    }

    /**
     * Poll every provider and upsert new items. Each provider is fault-tolerant:
     * a failure is logged and the others still run. Returns total inserted.
     */
    @Transactional
    public int refresh() {
        Instant now = Instant.now();
        int inserted = 0;
        for (NewsProvider provider : providers) {
            try {
                List<NewsItemDto> latest = provider.fetchLatest();
                for (NewsItemDto dto : latest) {
                    if (dto.url() == null || dto.url().isBlank()) {
                        continue;
                    }
                    if (repository.findBySourceAndUrl(dto.source(), dto.url()).isPresent()) {
                        continue;
                    }
                    repository.save(NewsItem.of(dto.source(), dto.title(), dto.body(),
                            dto.url(), dto.publishedAt(), now));
                    inserted++;
                }
            } catch (RuntimeException e) {
                log.warn("news refresh failed for source {}: {}", provider.source(), e.getMessage());
            }
        }
        log.debug("news refresh inserted {} items", inserted);
        return inserted;
    }

    /**
     * Lowercased alias -> symbol index over CRYPTO instruments, cached for
     * {@link #TAG_INDEX_TTL}. Aliases: the symbol base (KRW-BTC -> "btc"), the DB
     * English name ("bitcoin"), and the built-in KO/EN aliases for majors. On any
     * DB error returns the last good (or empty) index — tagging must never 500.
     */
    private Map<String, String> tagIndex() {
        Instant now = Instant.now();
        Map<String, String> current = tagIndex;
        if (now.isBefore(tagIndexExpiry)) {
            return current;
        }
        try {
            // Build alias and symbol->id indexes from one CRYPTO scan so they stay in sync.
            Map<String, String> built = new LinkedHashMap<>();
            Map<String, Long> ids = new LinkedHashMap<>();
            buildTagIndex(built, ids);
            tagIndex = built;
            symbolIdIndex = ids;
            tagIndexExpiry = now.plus(TAG_INDEX_TTL);
            return built;
        } catch (RuntimeException e) {
            log.warn("news tag index rebuild failed, reusing cached: {}", e.getMessage());
            // Push expiry forward so we don't hammer the DB on repeated failures.
            tagIndexExpiry = now.plus(TAG_INDEX_TTL);
            return current;
        }
    }

    private void buildTagIndex(Map<String, String> index, Map<String, Long> symbolToId) {
        // Insertion order = symbol order from the query → stable, deterministic tags.
        for (Instrument i : instrumentRepository.findByMarket("CRYPTO")) {
            String symbol = i.getSymbol();
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            // symbol -> id (first wins, mirrors putIfAbsent semantics of the alias index).
            symbolToId.putIfAbsent(symbol, i.getId());
            String base = baseOf(symbol);
            putAlias(index, base, symbol);
            putAlias(index, i.getName(), symbol);
            if (base != null) {
                for (String alias : NewsClassifier.EXTRA_NAMES
                        .getOrDefault(base.toUpperCase(Locale.ROOT), List.of())) {
                    putAlias(index, alias, symbol);
                }
            }
        }
    }

    /** "KRW-BTC" -> "BTC"; passthrough when there is no quote prefix. */
    private static String baseOf(String symbol) {
        int dash = symbol.indexOf('-');
        return dash >= 0 && dash + 1 < symbol.length() ? symbol.substring(dash + 1) : symbol;
    }

    private static void putAlias(Map<String, String> index, String alias, String symbol) {
        if (alias == null) {
            return;
        }
        String key = alias.trim().toLowerCase(Locale.ROOT);
        if (key.length() >= 2) {
            index.putIfAbsent(key, symbol);
        }
    }

    private static Instant sortTime(NewsItem n) {
        return n.getPublishedAt() != null ? n.getPublishedAt() : n.getCollectedAt();
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String s = source.trim().toUpperCase();
        if (!s.equals("TELEGRAM") && !s.equals("BLOOMBERG")) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "source must be TELEGRAM or BLOOMBERG");
        }
        return s;
    }

    /**
     * Normalize an optional symbol filter (e.g. {@code KRW-BTC}). Blank/null →
     * null (no filter). Uppercased to match the symbols produced by tagging.
     */
    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private static int clampSize(Integer pageSize) {
        if (pageSize == null) {
            return PAGE_SIZE;
        }
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }
}
