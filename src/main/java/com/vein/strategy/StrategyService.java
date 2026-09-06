package com.vein.strategy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.backtest.BacktestDto.Metrics;
import com.vein.backtest.BacktestDto.RunRequest;
import com.vein.backtest.BacktestDto.RunResponse;
import com.vein.backtest.BacktestService;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * Saved-strategy lifecycle: create / list / get / delete with ownership checks
 * (기획서 §12 seed), plus performance tracking — re-running a saved strategy's
 * backtest and accumulating metric snapshots as history (manual + scheduled).
 * Bad input always yields 400 (never 500); the scheduler never 500s.
 */
@Service
@Transactional
@Slf4j
public class StrategyService {

    /** Allowed strategy types; superset of {@code SignalType} (adds TRIANGLE). */
    private static final Set<String> ALLOWED_TYPES = Set.of("ABC", "TOP", "IMALOL", "TRIANGLE");

    private static final int DEFAULT_HISTORY_LIMIT = 30;
    private static final int MAX_HISTORY_LIMIT = 200;

    private final StrategyRepository strategyRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final BacktestService backtestService;
    private final ObjectMapper objectMapper;

    public StrategyService(StrategyRepository strategyRepository,
                           StrategyRunRepository strategyRunRepository,
                           BacktestService backtestService,
                           ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.strategyRunRepository = strategyRunRepository;
        this.backtestService = backtestService;
        this.objectMapper = objectMapper;
    }

    public StrategyDto.Response create(Long userId, StrategyDto.CreateRequest request) {
        List<ApiException.FieldError> errors = new ArrayList<>();

        String name = request == null ? null : request.name();
        if (name == null || name.isBlank()) {
            errors.add(new ApiException.FieldError("name", "must not be blank"));
        } else if (name.length() > 80) {
            errors.add(new ApiException.FieldError("name", "must be at most 80 characters"));
        }

        JsonNode params = request == null ? null : request.params();
        if (params == null || params.isNull() || !params.isObject()) {
            errors.add(new ApiException.FieldError("params", "must be an object"));
        }

        String type = textOf(params, "type");
        if (type == null || type.isBlank()) {
            errors.add(new ApiException.FieldError("params.type", "must not be blank"));
        } else if (!ALLOWED_TYPES.contains(type.trim().toUpperCase())) {
            errors.add(new ApiException.FieldError("params.type",
                    "must be one of ABC, TOP, IMALOL, TRIANGLE"));
        }

        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Validation failed", errors);
        }

        String market = textOf(params, "market");
        String timeframe = textOf(params, "timeframe");

        Strategy strategy = Strategy.builder()
                .userId(userId)
                .name(name.trim())
                .type(type.trim().toUpperCase())
                .market(market)
                .timeframe(timeframe)
                .params(params)
                .metrics(request.metrics())
                .build();
        return StrategyDto.Response.from(strategyRepository.save(strategy));
    }

    @Transactional(readOnly = true)
    public List<StrategyDto.Response> list(Long userId) {
        return strategyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(StrategyDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyDto.Response get(Long userId, Long id) {
        return StrategyDto.Response.from(ownedOrThrow(userId, id));
    }

    public void delete(Long userId, Long id) {
        strategyRepository.delete(ownedOrThrow(userId, id));
    }

    // --- Performance tracking --------------------------------------------

    /**
     * Owner-scoped manual run: re-run the saved strategy's backtest now, persist
     * a snapshot and return it. Unparseable/old params yield 400 (per spec the
     * manual endpoint may surface a bad-params error rather than silently skip).
     */
    public StrategyDto.RunSnapshot run(Long userId, Long id) {
        Strategy strategy = ownedOrThrow(userId, id);
        RunRequest request = toRunRequest(strategy);
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Strategy params are not a valid backtest request");
        }
        return StrategyDto.RunSnapshot.from(execute(strategy, request));
    }

    /** Owner-scoped history, newest first; {@code limit} clamped to 1..200. */
    @Transactional(readOnly = true)
    public List<StrategyDto.RunSnapshot> history(Long userId, Long id, Integer limit) {
        ownedOrThrow(userId, id);
        int capped = limit == null ? DEFAULT_HISTORY_LIMIT
                : Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        return strategyRunRepository
                .findByStrategyIdOrderByRunAtDesc(id, PageRequest.of(0, capped)).stream()
                .map(StrategyDto.RunSnapshot::from)
                .toList();
    }

    /**
     * Scheduler entry point: re-run every saved strategy's backtest so history
     * accrues. Per-strategy try/catch + continue — a single bad strategy
     * (malformed/old params, empty universe) never aborts the batch or 500s.
     */
    public void runAll() {
        List<Strategy> all = strategyRepository.findAll();
        int ok = 0;
        int failed = 0;
        for (Strategy strategy : all) {
            try {
                RunRequest request = toRunRequest(strategy);
                if (request == null) {
                    log.warn("skipping strategy {} (id={}): params are not a valid backtest request",
                            strategy.getName(), strategy.getId());
                    failed++;
                    continue;
                }
                execute(strategy, request);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("strategy run failed for id={}: {}", strategy.getId(), e.toString());
            }
        }
        log.info("strategy performance run complete: {} ok, {} skipped/failed of {}",
                ok, failed, all.size());
    }

    /**
     * Run the backtest and persist a {@link StrategyRun} snapshot (full metrics
     * JSON + denormalized scalars), then update the parent strategy's
     * {@code metrics} to this latest snapshot. Returns the persisted run, reloaded
     * so its DB-assigned {@code run_at} is populated.
     */
    private StrategyRun execute(Strategy strategy, RunRequest request) {
        RunResponse result = backtestService.run(request);
        Metrics metrics = result.metrics();
        JsonNode metricsJson = objectMapper.valueToTree(metrics);

        StrategyRun run = StrategyRun.builder()
                .strategyId(strategy.getId())
                .metrics(metricsJson)
                .tradeCount(metrics.tradeCount())
                .totalReturnPct(parseDecimal(metrics.totalReturnPct()))
                .winRate(parseDecimal(metrics.winRate()))
                .build();
        StrategyRun saved = strategyRunRepository.saveAndFlush(run);

        // Update the parent strategy's metrics to the latest snapshot.
        strategy.updateMetrics(metricsJson);
        strategyRepository.save(strategy);

        // Reload so the DB-defaulted run_at is present on the returned snapshot.
        return strategyRunRepository.findById(saved.getId()).orElse(saved);
    }

    /**
     * Convert the saved {@code params} JSON into a backtest {@link RunRequest}
     * via Jackson {@code treeToValue}. Returns null (caller decides 400 vs WARN)
     * when params are missing or cannot be bound — e.g. an old TRIANGLE type that
     * is no longer a valid {@code SignalType}.
     */
    private RunRequest toRunRequest(Strategy strategy) {
        JsonNode params = strategy.getParams();
        if (params == null || params.isNull() || !params.isObject()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(params, RunRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Strategy ownedOrThrow(Long userId, Long id) {
        Strategy strategy = strategyRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.STRATEGY_NOT_FOUND));
        if (!strategy.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return strategy;
    }

    /** Null-safe text accessor for a JSON object field; blank/missing -> null. */
    private static String textOf(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return (text == null || text.isBlank()) ? null : text;
    }
}
