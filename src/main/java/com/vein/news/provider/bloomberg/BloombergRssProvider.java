package com.vein.news.provider.bloomberg;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.vein.market.provider.ProviderException;
import com.vein.news.provider.NewsItemDto;
import com.vein.news.provider.NewsProvider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * REAL Bloomberg markets RSS provider (breaking_news_tab.py, Bloomberg leg).
 * Fetches {@code https://feeds.bloomberg.com/markets/news.rss} and parses the
 * RSS 2.0 {@code <channel><item>} entries with Jackson XML. HTML in titles/desc
 * is stripped; pubDate is parsed as RFC-822 to UTC.
 */
@Component
public class BloombergRssProvider implements NewsProvider {

    public static final String SOURCE = "BLOOMBERG";
    private static final int LIMIT = 30;
    /** RFC-822 with English month/weekday names, e.g. {@code Fri, 13 Jun 2026 09:30:00 GMT}. */
    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss[ zzz][ Z]", Locale.ENGLISH);

    private final RestClient restClient;
    private final String feedUrl;
    private final XmlMapper xmlMapper = (XmlMapper) new XmlMapper().findAndRegisterModules();

    public BloombergRssProvider(
            @Value("${vein.bloomberg.feed-url:https://feeds.bloomberg.com/markets/news.rss}") String feedUrl) {
        this.feedUrl = feedUrl;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "VEIN/1.0 (+Bloomberg RSS reader)")
                .defaultHeader("accept", "application/rss+xml, application/xml, text/xml")
                .build();
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    @RateLimiter(name = "bloomberg")
    @Retry(name = "bloomberg")
    @CircuitBreaker(name = "bloomberg")
    public List<NewsItemDto> fetchLatest() {
        String xml;
        try {
            xml = restClient.get().uri(feedUrl).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new ProviderException("Bloomberg RSS fetch failed", e);
        }
        if (xml == null || xml.isBlank()) {
            throw new ProviderException("Bloomberg RSS returned empty body");
        }
        Rss rss;
        try {
            rss = xmlMapper.readValue(xml, Rss.class);
        } catch (Exception e) {
            throw new ProviderException("Bloomberg RSS parse failed", e);
        }
        if (rss == null || rss.channel() == null || rss.channel().items() == null) {
            return List.of();
        }
        List<NewsItemDto> out = new ArrayList<>();
        for (RssItem item : rss.channel().items()) {
            String title = clean(item.title());
            String body = clean(item.description());
            String link = item.link() == null ? null : item.link().trim();
            if ((title == null || title.isBlank()) && (body == null || body.isBlank())) {
                continue;
            }
            if (link == null || link.isBlank()) {
                // RSS items always carry a guid/link; skip the rare malformed one.
                continue;
            }
            out.add(new NewsItemDto(SOURCE, title, body, link, parseDate(item.pubDate())));
            if (out.size() >= LIMIT) {
                break;
            }
        }
        return out;
    }

    /** RFC-822 pubDate -> UTC Instant; null on parse failure (kept, just no time). */
    static Instant parseDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return null;
        }
        try {
            return Instant.from(RFC_822.parse(pubDate.trim()));
        } catch (RuntimeException e) {
            try {
                return Instant.parse(pubDate.trim().replace("Z", "Z"));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    /** Unescape entities (handled by Jackson) and strip residual HTML tags / whitespace. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.replaceAll("<[^>]+>", " ");
        stripped = stripped.replaceAll("\\s+", " ").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
