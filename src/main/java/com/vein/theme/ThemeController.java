package com.vein.theme;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Theme/sector endpoints (API_CONTRACT §6). Classification is loaded from the
 * legacy JSON maps with a keyword heuristic fallback. The model is seeded lazily
 * on first access (and periodically refreshed by {@link ThemeScheduler}).
 */
@RestController
@RequestMapping("/api/v1/themes")
@Tag(name = "Theme", description = "테마/섹터 — CRYPTO/US/KR classification")
@Slf4j
public class ThemeController {

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @GetMapping
    @Operation(summary = "Theme list",
            description = "?market=CRYPTO|US|KR & q= & unclassified_only & low_confidence_only.")
    public ApiResponse<List<ThemeDto.ThemeRow>> themes(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String q,
            @RequestParam(name = "unclassified_only", defaultValue = "false") boolean unclassifiedOnly,
            @RequestParam(name = "low_confidence_only", defaultValue = "false") boolean lowConfidenceOnly) {
        ensureSeeded();
        return ApiResponse.of(themeService.list(market, q, unclassifiedOnly, lowConfidenceOnly));
    }

    @GetMapping("/{id}/constituents")
    @Operation(summary = "Theme constituents", description = "Members + classification source/confidence.")
    public ApiResponse<ThemeDto.Constituents> constituents(@PathVariable Long id) {
        ensureSeeded();
        return ApiResponse.of(themeService.constituents(id));
    }

    /** Lazily build the theme model the first time it's queried. */
    private void ensureSeeded() {
        if (!themeService.isSeeded()) {
            try {
                themeService.seed();
            } catch (RuntimeException e) {
                log.warn("theme lazy seed failed: {}", e.getMessage());
            }
        }
    }
}
