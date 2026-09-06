package com.vein.condition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.TimeUtil;
import com.vein.common.Timeframe;
import com.vein.condition.ConditionDto.Condition;
import com.vein.condition.ConditionDto.Match;
import com.vein.condition.ConditionDto.RuleResponse;
import com.vein.condition.ConditionDto.RunRequest;
import com.vein.condition.ConditionDto.RunResponse;
import com.vein.condition.ConditionDto.SaveRequest;
import com.vein.indicator.Indicators;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.notification.NotificationService;

@Service
public class ConditionScannerService {

    private static final Set<String> MARKETS = Set.of("CRYPTO", "US", "KOSPI", "KOSDAQ");
    private static final Set<String> INDICATORS =
            Set.of("RSI", "VOLUME_RATIO", "PRICE", "MA5", "MACD_HISTOGRAM");
    private static final Set<String> OPERATORS = Set.of("<", "<=", ">", ">=");
    private static final int LOOKBACK = 140;
    private static final int MAX_RESULTS = 100;

    private final InstrumentRepository instrumentRepository;
    private final CandleRepository candleRepository;
    private final ScannerRuleRepository ruleRepository;
    private final ScannerRuleRunRepository runRepository;
    private final ScannerRuleMatchRepository matchRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ConditionScannerService(InstrumentRepository instrumentRepository,
                                   CandleRepository candleRepository,
                                   ScannerRuleRepository ruleRepository,
                                   ScannerRuleRunRepository runRepository,
                                   ScannerRuleMatchRepository matchRepository,
                                   NotificationService notificationService,
                                   ObjectMapper objectMapper) {
        this.instrumentRepository = instrumentRepository;
        this.candleRepository = candleRepository;
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.matchRepository = matchRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RunResponse run(RunRequest request) {
        Validated validated = validate(request);
        List<Instrument> instruments =
                instrumentRepository.findByMarketAndStatus(validated.market(), "ACTIVE");
        List<Match> matches = new ArrayList<>();
        int evaluated = 0;
        for (Instrument instrument : instruments) {
            Metrics metrics = metrics(instrument.getId(), validated.timeframe());
            if (metrics == null) continue;
            evaluated++;
            List<String> matched = new ArrayList<>();
            int passed = 0;
            for (Condition condition : validated.conditions()) {
                boolean ok = evaluate(condition, metrics);
                if (ok) {
                    passed++;
                    matched.add(describe(condition));
                }
            }
            boolean selected = "AND".equals(validated.logic())
                    ? passed == validated.conditions().size()
                    : passed > 0;
            if (selected && matches.size() < MAX_RESULTS) {
                matches.add(new Match(
                        instrument.getId(), instrument.getSymbol(), instrument.getName(),
                        instrument.getMarket(), str(metrics.price()), str(metrics.rsi()),
                        str(metrics.volumeRatio()), str(metrics.ma5()), str(metrics.ma20()),
                        str(metrics.macdHistogram()), matched));
            }
        }
        return new RunResponse(evaluated, matches.size(), matches);
    }

    @Transactional
    public RuleResponse save(Long userId, SaveRequest request) {
        Validated validated = validate(new RunRequest(
                request.market(), request.timeframe(), request.logic(), request.conditions()));
        if (request.name() == null || request.name().isBlank() || request.name().length() > 80) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "name must be between 1 and 80 characters");
        }
        JsonNode conditions = objectMapper.valueToTree(validated.conditions());
        ScannerRule saved = ruleRepository.save(ScannerRule.create(
                userId, request.name().trim(), validated.market(), validated.timeframe(),
                validated.logic(), conditions));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(Long userId) {
        return ruleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::response).toList();
    }

    @Transactional
    public RuleResponse update(Long userId, Long id, ConditionDto.UpdateRuleRequest request) {
        ScannerRule rule = owned(userId, id);
        if (request != null && request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        return response(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ScannerRule rule = owned(userId, id);
        ruleRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public ConditionDto.SimulationResponse simulate(Long userId, Long id) {
        ScannerRule rule = owned(userId, id);
        List<Condition> conditions = objectMapper.convertValue(
                rule.getConditions(), new TypeReference<List<Condition>>() { });
        RunResponse result = run(new RunRequest(
                rule.getMarket(), rule.getTimeframe(), rule.getLogic(), conditions));
        BigDecimal rate = matchRate(result.evaluatedCount(), result.matchedCount());
        String grade = frequencyGrade(rate);
        return new ConditionDto.SimulationResponse(
                rule.getId(), result.evaluatedCount(), result.matchedCount(),
                rate.stripTrailingZeros().toPlainString(), grade,
                result.items().stream().limit(5).toList());
    }

    @Transactional(readOnly = true)
    public List<ConditionDto.RuleRunResponse> history(Long userId, Long id) {
        owned(userId, id);
        return runRepository.findByRuleIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))
                .stream()
                .map(ScannerRuleRun::toResponse)
                .toList();
    }

    @Transactional
    public void evaluateEnabledRules() {
        for (ScannerRule rule : ruleRepository.findByEnabledTrueOrderByCreatedAtAsc()) {
            evaluateRuleForAlerts(rule);
        }
    }

    private void evaluateRuleForAlerts(ScannerRule rule) {
        List<Condition> conditions = objectMapper.convertValue(
                rule.getConditions(), new TypeReference<List<Condition>>() { });
        RunResponse result = run(new RunRequest(
                rule.getMarket(), rule.getTimeframe(), rule.getLogic(), conditions));
        Instant now = Instant.now();
        Set<Long> currentIds = new HashSet<>();
        int notifications = 0;

        for (Match item : result.items()) {
            currentIds.add(item.instrumentId());
            ScannerRuleMatch match = matchRepository.findByRuleIdAndInstrumentId(
                            rule.getId(), item.instrumentId())
                    .orElseGet(() -> ScannerRuleMatch.create(
                            rule.getId(), rule.getUserId(), item.instrumentId(), now));
            boolean reactivated = match.getId() != null && match.reactivate(now);
            if (match.getId() == null || reactivated || match.getLastNotifiedAt() == null) {
                notificationService.createSystemInApp(
                        rule.getUserId(),
                        "Scanner match: " + rule.getName(),
                        item.symbol() + " matched " + rule.getMarket() + " "
                                + rule.getTimeframe() + " conditions");
                match.markNotified(now);
                notifications++;
            }
            matchRepository.save(match);
        }

        if (currentIds.isEmpty()) {
            matchRepository.findByRuleIdAndActiveTrue(rule.getId())
                    .forEach(match -> {
                        match.deactivate();
                        matchRepository.save(match);
                    });
        } else {
            matchRepository.findByRuleIdAndInstrumentIdNotInAndActiveTrue(rule.getId(), currentIds)
                    .forEach(match -> {
                        match.deactivate();
                        matchRepository.save(match);
                    });
        }

        BigDecimal rate = matchRate(result.evaluatedCount(), result.matchedCount());
        runRepository.save(ScannerRuleRun.create(
                rule.getId(), rule.getUserId(), result.evaluatedCount(), result.matchedCount(),
                rate, frequencyGrade(rate), notifications));
    }

    private Validated validate(RunRequest request) {
        if (request == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String market = upper(request.market());
        if (!MARKETS.contains(market)) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "Unsupported market");
        }
        String timeframe;
        try {
            timeframe = Timeframe.fromCode(request.timeframe()).code();
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "Unsupported timeframe");
        }
        String logic = upper(request.logic());
        if (!Set.of("AND", "OR").contains(logic)) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "logic must be AND or OR");
        }
        List<Condition> conditions = request.conditions();
        if (conditions == null || conditions.isEmpty() || conditions.size() > 8) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "conditions must contain between 1 and 8 items");
        }
        for (Condition condition : conditions) validateCondition(condition);
        return new Validated(market, timeframe, logic, conditions);
    }

    private void validateCondition(Condition condition) {
        if (condition == null || !INDICATORS.contains(upper(condition.indicator()))
                || !OPERATORS.contains(condition.operator())) {
            throw new ApiException(ErrorCode.INVALID_FILTER, "Unsupported condition");
        }
        String indicator = upper(condition.indicator());
        if (Set.of("PRICE", "MA5").contains(indicator)) {
            if (!"MA20".equals(upper(condition.target()))) {
                throw new ApiException(ErrorCode.INVALID_FILTER, indicator + " target must be MA20");
            }
        } else {
            decimal(condition.value(), "condition value");
        }
    }

    private Metrics metrics(Long instrumentId, String timeframe) {
        List<Candle> newest = candleRepository.findLatest(
                instrumentId, timeframe, null, null, PageRequest.of(0, LOOKBACK));
        if (newest.size() < 27) return null;
        List<Candle> ascending = newest.stream()
                .sorted((a, b) -> a.openTime().compareTo(b.openTime())).toList();
        List<BigDecimal> closes = ascending.stream().map(Candle::close).toList();
        BigDecimal price = closes.get(closes.size() - 1);
        BigDecimal rsi = Indicators.last(Indicators.rsi(closes, 14));
        BigDecimal ma5 = Indicators.lastSma(closes, 5);
        BigDecimal ma20 = Indicators.lastSma(closes, 20);
        BigDecimal macd = Indicators.last(Indicators.macd(closes, 12, 26, 9).histogram());
        BigDecimal volumeRatio = volumeRatio(ascending);
        if (price == null || rsi == null || ma5 == null || ma20 == null || macd == null) return null;
        return new Metrics(price, rsi, volumeRatio, ma5, ma20, macd);
    }

    private BigDecimal volumeRatio(List<Candle> ascending) {
        int last = ascending.size() - 1;
        BigDecimal current = ascending.get(last).volume();
        if (current == null) return null;
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = Math.max(0, last - 20); i < last; i++) {
            BigDecimal volume = ascending.get(i).volume();
            if (volume != null) {
                sum = sum.add(volume);
                count++;
            }
        }
        if (count == 0 || sum.signum() == 0) return null;
        return current.divide(sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP),
                4, RoundingMode.HALF_UP);
    }

    private boolean evaluate(Condition condition, Metrics metrics) {
        String indicator = upper(condition.indicator());
        BigDecimal left = switch (indicator) {
            case "RSI" -> metrics.rsi();
            case "VOLUME_RATIO" -> metrics.volumeRatio();
            case "PRICE" -> metrics.price();
            case "MA5" -> metrics.ma5();
            case "MACD_HISTOGRAM" -> metrics.macdHistogram();
            default -> null;
        };
        BigDecimal right = Set.of("PRICE", "MA5").contains(indicator)
                ? metrics.ma20()
                : decimal(condition.value(), "condition value");
        if (left == null || right == null) return false;
        int comparison = left.compareTo(right);
        return switch (condition.operator()) {
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            default -> false;
        };
    }

    private String describe(Condition condition) {
        String right = condition.target() == null ? condition.value() : condition.target();
        return upper(condition.indicator()) + " " + condition.operator() + " " + right;
    }

    private ScannerRule owned(Long userId, Long id) {
        ScannerRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!rule.getUserId().equals(userId)) throw new ApiException(ErrorCode.FORBIDDEN);
        return rule;
    }

    private RuleResponse response(ScannerRule rule) {
        ConditionDto.RuleRunResponse latestRun = runRepository
                .findByRuleIdOrderByCreatedAtDesc(rule.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(ScannerRuleRun::toResponse)
                .orElse(null);
        return new RuleResponse(rule.getId(), rule.getName(), rule.getMarket(),
                rule.getTimeframe(), rule.getLogic(), rule.getConditions(), rule.isEnabled(),
                TimeUtil.toIso(rule.getCreatedAt()), latestRun);
    }

    private BigDecimal matchRate(int evaluatedCount, int matchedCount) {
        return evaluatedCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(matchedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(evaluatedCount), 2, RoundingMode.HALF_UP);
    }

    private String frequencyGrade(BigDecimal rate) {
        return rate.compareTo(BigDecimal.valueOf(2)) < 0 ? "LOW"
                : rate.compareTo(BigDecimal.TEN) < 0 ? "MEDIUM" : "HIGH";
    }

    private BigDecimal decimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, field + " must be numeric");
        }
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String str(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private record Validated(String market, String timeframe, String logic,
                             List<Condition> conditions) {
    }

    private record Metrics(BigDecimal price, BigDecimal rsi, BigDecimal volumeRatio,
                           BigDecimal ma5, BigDecimal ma20, BigDecimal macdHistogram) {
    }
}
