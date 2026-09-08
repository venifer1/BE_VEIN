package com.vein.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vein.common.ApiResponse;
import com.vein.common.TimeUtil;
import com.vein.derivatives.DerivativesService;
import com.vein.funding.FundingService;
import com.vein.ingestion.IngestionRun;
import com.vein.ingestion.IngestionRunRepository;
import com.vein.market.index.MarketIndexService;
import com.vein.market.kimchi.KimchiPremiumService;
import com.vein.news.NewsService;
import com.vein.tvl.TvlService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public system-status endpoint. Reports build version, provider freshness and
 * scanner status derived loosely from recent ingestion runs. Never throws on
 * missing data — degrades to UNKNOWN.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Public system/operational status")
public class OpsStatusController {

    private static final Logger log = LoggerFactory.getLogger(OpsStatusController.class);
    private static final String BUILD_VERSION = "1.0.0";
    private static final Duration FRESH_WINDOW = Duration.ofHours(1);
    private static final Duration TERMINAL_FRESH_WINDOW = Duration.ofMinutes(5);
    /** Phase-3 feeds refresh on longer cadences (TVL 60s, funding 5m, news 15s). */
    private static final Duration AUX_FRESH_WINDOW = Duration.ofMinutes(15);
    private static final int RECENT_LIMIT = 50;

    /** Providers wired in Phase 1 (real or stubbed) that report data freshness. */
    private static final List<String> PHASE1_PROVIDERS = List.of(
            "upbit", "binance", "coingecko", "yfinance", "pykrx");
    /** Phase-3 aux providers — now report real freshness from their snapshots. */
    private static final List<String> PHASE3_PROVIDERS = List.of(
            "defillama", "bybit", "telegram", "bloomberg", "binance_futures");

    private final IngestionRunRepository ingestionRunRepository;
    private final MarketIndexService marketIndexService;
    private final KimchiPremiumService kimchiPremiumService;
    private final TvlService tvlService;
    private final FundingService fundingService;
    private final NewsService newsService;
    private final DerivativesService derivativesService;
    private final SidecarHealth sidecarHealth;

    public OpsStatusController(IngestionRunRepository ingestionRunRepository,
                              MarketIndexService marketIndexService,
                              KimchiPremiumService kimchiPremiumService,
                              TvlService tvlService,
                              FundingService fundingService,
                              NewsService newsService,
                              DerivativesService derivativesService,
                              SidecarHealth sidecarHealth) {
        this.ingestionRunRepository = ingestionRunRepository;
        this.marketIndexService = marketIndexService;
        this.kimchiPremiumService = kimchiPremiumService;
        this.tvlService = tvlService;
        this.fundingService = fundingService;
        this.newsService = newsService;
        this.derivativesService = derivativesService;
        this.sidecarHealth = sidecarHealth;
    }

    /** Sidecar-backed providers that silently fall back to a SYNTHETIC stub when the sidecar is down. */
    private static final List<String> SIDECAR_BACKED = List.of("yfinance", "pykrx", "telegram");

    /** {@code source} ∈ REAL|STUB: STUB means this provider is currently serving synthetic fallback. */
    public record ProviderStatus(String provider, String freshness, String source, String lastRunAt) {
    }

    public record ScannerStatus(String status, String lastRunAt) {
    }

    /** Live sidecar reachability so the UI can flag synthetic-stub fallback. */
    public record SidecarStatus(boolean healthy, String url) {
    }

    public record SystemStatusDto(String buildVersion, String time, List<ProviderStatus> providers,
                                  ScannerStatus scanner, SidecarStatus sidecar) {
    }

    @GetMapping("/status")
    @Operation(summary = "System status (public)")
    public ApiResponse<SystemStatusDto> status() {
        Instant now = Instant.now();
        boolean sidecarUp = sidecarHealth.healthy();
        List<ProviderStatus> providers = new ArrayList<>();
        ScannerStatus scanner = new ScannerStatus("UNKNOWN", null);

        try {
            // Latest ingestion run per provider (candle pollers).
            List<IngestionRun> recent = ingestionRunRepository.findAll().stream()
                    .filter(r -> r.getStartedAt() != null)
                    .sorted(Comparator.comparing(IngestionRun::getStartedAt).reversed())
                    .limit(RECENT_LIMIT)
                    .toList();
            Map<String, IngestionRun> latestByProvider = new LinkedHashMap<>();
            IngestionRun latestOverall = null;
            for (IngestionRun run : recent) {
                if (run.getProvider() != null) {
                    latestByProvider.putIfAbsent(run.getProvider().toLowerCase(), run);
                }
                if (latestOverall == null) {
                    latestOverall = run;
                }
            }

            // Terminal data freshness (indices / kimchi) feeds coingecko/upbit/binance.
            Instant indicesAt = safe(marketIndexService::lastCollectedAt);
            Instant kimchiAt = safe(kimchiPremiumService::lastCollectedAt);

            for (String p : PHASE1_PROVIDERS) {
                IngestionRun run = latestByProvider.get(p);
                Instant lastAt = run != null ? run.getStartedAt() : null;
                // Augment with terminal freshness where the poller doesn't cover it.
                if ("coingecko".equals(p)) {
                    lastAt = mostRecent(lastAt, indicesAt);
                } else if ("binance".equals(p)) {
                    lastAt = mostRecent(lastAt, kimchiAt);
                } else if ("upbit".equals(p)) {
                    lastAt = mostRecent(lastAt, mostRecent(indicesAt, kimchiAt));
                }
                providers.add(providerStatus(p, lastAt, now, sourceOf(p, sidecarUp)));
            }
            // Phase-3 aux providers: real freshness from their snapshot/last-poll times.
            Instant defillamaAt = safe(tvlService::lastCollectedAt);
            Instant bybitAt = safe(fundingService::lastCollectedAt);
            Instant telegramAt = safe(() -> newsService.lastCollectedAt("TELEGRAM"));
            Instant bloombergAt = safe(() -> newsService.lastCollectedAt("BLOOMBERG"));
            Instant derivativesAt = safe(derivativesService::lastCollectedAt);
            providers.add(auxStatus("defillama", defillamaAt, now, sourceOf("defillama", sidecarUp)));
            providers.add(auxStatus("bybit", bybitAt, now, sourceOf("bybit", sidecarUp)));
            providers.add(auxStatus("telegram", telegramAt, now, sourceOf("telegram", sidecarUp)));
            providers.add(auxStatus("bloomberg", bloombergAt, now, sourceOf("bloomberg", sidecarUp)));
            providers.add(auxStatus("binance_futures", derivativesAt, now, sourceOf("binance_futures", sidecarUp)));

            if (latestOverall != null) {
                String runStatus = latestOverall.getStatus() != null ? latestOverall.getStatus() : "UNKNOWN";
                scanner = new ScannerStatus(runStatus, TimeUtil.toIso(latestOverall.getStartedAt()));
            }
        } catch (RuntimeException e) {
            log.warn("Failed to derive system status; returning placeholders", e);
            providers.clear();
            for (String p : PHASE1_PROVIDERS) {
                providers.add(new ProviderStatus(p, "UNKNOWN", sourceOf(p, sidecarUp), null));
            }
            for (String p : PHASE3_PROVIDERS) {
                providers.add(new ProviderStatus(p, "UNKNOWN", sourceOf(p, sidecarUp), null));
            }
        }

        SidecarStatus sidecar = new SidecarStatus(sidecarUp, sidecarHealth.url());
        return ApiResponse.of(new SystemStatusDto(BUILD_VERSION, TimeUtil.toIso(now), providers, scanner, sidecar));
    }

    /** REAL, unless a sidecar-backed provider is serving synthetic stub because the sidecar is down. */
    private static String sourceOf(String provider, boolean sidecarUp) {
        return (SIDECAR_BACKED.contains(provider) && !sidecarUp) ? "STUB" : "REAL";
    }

    private static ProviderStatus providerStatus(String provider, Instant lastAt, Instant now, String source) {
        if (lastAt == null) {
            return new ProviderStatus(provider, "UNKNOWN", source, null);
        }
        // yfinance/pykrx are stubbed: they only have data once a backfill ran.
        Duration window = (provider.equals("yfinance") || provider.equals("pykrx"))
                ? FRESH_WINDOW : TERMINAL_FRESH_WINDOW;
        // Candle pollers (upbit candles) use the hourly window; pick the looser one.
        if (Duration.between(lastAt, now).compareTo(window.compareTo(FRESH_WINDOW) > 0 ? window : FRESH_WINDOW) <= 0) {
            return new ProviderStatus(provider, "FRESH", source, TimeUtil.toIso(lastAt));
        }
        return new ProviderStatus(provider, "DELAYED", source, TimeUtil.toIso(lastAt));
    }

    /** Freshness for a Phase-3 aux provider using the longer aux window. */
    private static ProviderStatus auxStatus(String provider, Instant lastAt, Instant now, String source) {
        if (lastAt == null) {
            return new ProviderStatus(provider, "UNKNOWN", source, null);
        }
        boolean fresh = Duration.between(lastAt, now).compareTo(AUX_FRESH_WINDOW) <= 0;
        return new ProviderStatus(provider, fresh ? "FRESH" : "DELAYED", source, TimeUtil.toIso(lastAt));
    }

    private static Instant mostRecent(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static Instant safe(java.util.function.Supplier<Instant> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
