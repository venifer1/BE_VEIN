package com.vein.news;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    Optional<NewsItem> findBySourceAndUrl(String source, String url);

    Optional<NewsItem> findTopByOrderByCollectedAtDesc();

    Optional<NewsItem> findTopBySourceOrderByCollectedAtDesc(String source);

    /**
     * Newest-first page. {@code source} null = all sources. Cursor is the
     * {@code (published_at, id)} of the last seen row (both null = first page).
     * Rows with a null published_at sort last (treated via COALESCE to epoch 0).
     */
    @Query("""
            select n from NewsItem n
            where (:source is null or n.source = :source)
              and (cast(:cursorTs as Instant) is null
                   or coalesce(n.publishedAt, n.collectedAt) < :cursorTs
                   or (coalesce(n.publishedAt, n.collectedAt) = :cursorTs and n.id < :cursorId))
            order by coalesce(n.publishedAt, n.collectedAt) desc, n.id desc
            """)
    List<NewsItem> findPage(@Param("source") String source,
                            @Param("cursorTs") Instant cursorTs,
                            @Param("cursorId") Long cursorId,
                            Pageable pageable);

    /**
     * Newest-first window for symbol-tag filtering. {@code source} null = all
     * sources. No cursor: callers fetch a bounded recent window (via Pageable),
     * compute tags on read, filter by symbol, then page the filtered set.
     */
    @Query("""
            select n from NewsItem n
            where (:source is null or n.source = :source)
            order by coalesce(n.publishedAt, n.collectedAt) desc, n.id desc
            """)
    List<NewsItem> findRecent(@Param("source") String source, Pageable pageable);
}
