package com.vein.news;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Breaking-news feed (API_CONTRACT §5). Server collects Telegram(coinness) +
 * Bloomberg RSS; the app reads. Newest-first, cursor-paginated.
 */
@RestController
@RequestMapping("/api/v1/news")
@Tag(name = "News", description = "속보 — Telegram + Bloomberg feed (read-only)")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    @Operation(summary = "News feed",
            description = "?source=TELEGRAM|BLOOMBERG & ?symbol=KRW-BTC (filter to items tagged "
                    + "with that symbol) & cursor. Items: source, title, body, url, "
                    + "published_at, is_new. Cursor by published_at desc.")
    public ApiResponse<List<NewsDto>> news(@RequestParam(required = false) String source,
                                           @RequestParam(required = false) String symbol,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(name = "page_size", required = false) Integer pageSize) {
        NewsService.Page page = newsService.list(source, symbol, cursor, pageSize);
        return ApiResponse.list(page.items(), page.nextCursor());
    }
}
