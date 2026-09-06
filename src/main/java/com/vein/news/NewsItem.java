package com.vein.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A collected breaking-news item (V8 {@code news_items}). One row per
 * (source, url); the scheduler upserts so re-fetches don't duplicate.
 */
@Entity
@Table(name = "news_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    private NewsItem(String source, String title, String body, String url,
                     Instant publishedAt, Instant collectedAt) {
        this.source = source;
        this.title = title;
        this.body = body;
        this.url = url;
        this.publishedAt = publishedAt;
        this.collectedAt = collectedAt;
    }

    public static NewsItem of(String source, String title, String body, String url,
                              Instant publishedAt, Instant collectedAt) {
        return new NewsItem(source, title, body, url, publishedAt, collectedAt);
    }
}
