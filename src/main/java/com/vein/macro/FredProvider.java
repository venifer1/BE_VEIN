package com.vein.macro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * FRED (St. Louis Fed) provider for macro series — 금리(DGS2/DGS3MO/DGS10),
 * M2(M2SL), 달러인덱스(DTWEXBGS). <b>키가 없으면 즉시 빈 결과</b>를 돌려주고 HTTP를
 *치지 않는다(사이드카 스텁-폴백과 동일 철학). 상류 실패에도 절대 throw하지 않는다 —
 * 거시 지표는 있으면 보강, 없으면 국면 판정이 내부 데이터로 계속 돌아간다.
 */
@Component
@Slf4j
public class FredProvider {

    private final RestClient restClient;
    private final String apiKey;

    public FredProvider(@Value("${vein.macro.fred.base-url:https://api.stlouisfed.org/fred}") String baseUrl,
                        @Value("${vein.macro.fred.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    /** True when a FRED key is configured; callers can skip UI/labels otherwise. */
    public boolean enabled() {
        return !apiKey.isBlank();
    }

    /** One observation: numeric value on a calendar date (yyyy-MM-dd). */
    public record Observation(String date, BigDecimal value) {
    }

    /**
     * Latest {@code limit} observations for a series, newest first, missing (".")
     * values dropped. Empty when no key or on any upstream failure — never throws.
     */
    public List<Observation> latest(String seriesId, int limit) {
        if (apiKey.isBlank()) {
            return List.of();
        }
        FredResponse resp;
        try {
            resp = restClient.get()
                    .uri(uri -> uri.path("/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", apiKey)
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new IllegalStateException("FRED " + seriesId + " HTTP " + res.getStatusCode());
                    })
                    .body(FredResponse.class);
        } catch (RuntimeException e) {
            log.warn("FRED {} unavailable: {}", seriesId, e.getMessage());
            return List.of();
        }
        if (resp == null || resp.observations() == null) {
            return List.of();
        }
        List<Observation> out = new ArrayList<>(resp.observations().size());
        for (FredResponse.Obs o : resp.observations()) {
            if (o.value() == null || o.value().isBlank() || ".".equals(o.value())) {
                continue;
            }
            try {
                out.add(new Observation(o.date(), new BigDecimal(o.value())));
            } catch (NumberFormatException ignore) {
                // skip non-numeric
            }
        }
        return out;
    }

    /** Latest single value for a series, or null. */
    public Observation latestOne(String seriesId) {
        List<Observation> obs = latest(seriesId, 1);
        return obs.isEmpty() ? null : obs.get(0);
    }
}
