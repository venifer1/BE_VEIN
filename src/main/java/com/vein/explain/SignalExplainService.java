package com.vein.explain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.indicator.IndicatorService;
import com.vein.indicator.IndicatorService.IndicatorSummary;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.news.NewsDto;
import com.vein.news.NewsSentiment;
import com.vein.news.NewsService;
import com.vein.signal.PatternSignal;
import com.vein.signal.PatternSignalRepository;
import com.vein.signal.SignalPerformanceDto.SummaryRow;
import com.vein.signal.SignalPerformanceService;
import com.vein.signal.SignalStatus;

@Service
@Transactional(readOnly = true)
public class SignalExplainService {

    private static final String TEMPLATE_VERSION = "rules-ko-v1";

    private final PatternSignalRepository signalRepository;
    private final InstrumentRepository instrumentRepository;
    private final CandleRepository candleRepository;
    private final IndicatorService indicatorService;
    private final NewsService newsService;
    private final SignalPerformanceService performanceService;
    private final PatternScoreProperties properties;

    public SignalExplainService(PatternSignalRepository signalRepository,
                                InstrumentRepository instrumentRepository,
                                CandleRepository candleRepository,
                                IndicatorService indicatorService,
                                NewsService newsService,
                                SignalPerformanceService performanceService,
                                PatternScoreProperties properties) {
        this.signalRepository = signalRepository;
        this.instrumentRepository = instrumentRepository;
        this.candleRepository = candleRepository;
        this.indicatorService = indicatorService;
        this.newsService = newsService;
        this.performanceService = performanceService;
        this.properties = properties;
    }

    public SignalExplainDto explain(Long id) {
        PatternSignal signal = signalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SIGNAL_NOT_FOUND));
        Assessment a = assess(signal);
        PatternScoreCalculator.Scores scores = a.scores();

        SummaryRow performance = performanceService.summary(
                        signal.getType(), signal.getMarket(), signal.getTimeframe(), "1d").stream()
                .findFirst().orElse(null);
        SignalExplainDto.Confidence confidence = confidence(performance);

        List<String> reasons = reasons(signal, a.volumeRatio(), a.indicators(), a.positiveNews());
        List<String> risks = risks(signal, a.averageRangePct(), a.indicators(), a.negativeNews(), confidence);
        List<String> nextChecks = nextChecks(signal);
        String riskGuard = riskGuard(signal, a.averageRangePct(), a.indicators());

        List<SignalExplainDto.ScoreFactor> factors = List.of(
                new SignalExplainDto.ScoreFactor("COMPLETION", "패턴 완성도", scores.completion(),
                        properties.completionWeight(), "탐지기 구조 점수 " + value(signal.getScore(), "점")),
                new SignalExplainDto.ScoreFactor("VOLUME", "거래량 증가", scores.volume(),
                        properties.volumeWeight(), "최근 거래량/20봉 평균 " + value(a.volumeRatio(), "배")),
                new SignalExplainDto.ScoreFactor("TREND", "추세 강도", scores.trend(),
                        properties.trendWeight(), trendDetail(a.currentPrice(), a.ma5(), a.ma20(), a.macdHistogram(), a.rsi())),
                new SignalExplainDto.ScoreFactor("VOLATILITY", "변동성 적정성", scores.volatility(),
                        properties.volatilityWeight(), "최근 평균 고저폭 " + value(a.averageRangePct(), "%")),
                new SignalExplainDto.ScoreFactor("NEWS", "뉴스 모멘텀", scores.news(),
                        properties.newsWeight(), "관련 긍정 " + a.positiveNews() + "건 · 부정 " + a.negativeNews() + "건"));

        return new SignalExplainDto(
                "sig_" + signal.getId(), scores.total(), riskGuard, factors,
                reasons, risks, nextChecks, confidence, TEMPLATE_VERSION);
    }

    /**
     * 종합 Pattern Score(0~100 총점)만 계산한다. Explain 전체(근거·위험·Confidence)를 만들지 않고
     * 점수만 필요할 때(예: {@code SignalPatternScoreService}의 top 정렬 영속화, R90) 쓰는 경량 진입점.
     * explain()과 동일한 입력 수집·계산 경로({@link #assess})를 공유해 두 값이 절대 어긋나지 않는다.
     */
    public int computeScore(PatternSignal signal) {
        return assess(signal).scores().total();
    }

    /** explain()과 pattern-score 영속화가 공유하는 입력 수집 + 점수 계산 결과. */
    private record Assessment(BigDecimal volumeRatio, BigDecimal averageRangePct,
                             IndicatorSummary indicators, BigDecimal currentPrice, BigDecimal ma5,
                             BigDecimal ma20, BigDecimal macdHistogram, BigDecimal rsi,
                             int positiveNews, int negativeNews,
                             PatternScoreCalculator.Scores scores) {
    }

    private Assessment assess(PatternSignal signal) {
        Instrument instrument = instrumentRepository.findById(signal.getInstrumentId()).orElse(null);

        List<Candle> candles = candleRepository.findLatest(
                signal.getInstrumentId(), signal.getTimeframe(), null, null, PageRequest.of(0, 21));
        BigDecimal volumeRatio = volumeRatio(candles);
        BigDecimal averageRangePct = averageRangePct(candles);

        IndicatorSummary indicators = safeIndicators(signal);
        BigDecimal currentPrice = signal.getCurrentPrice();
        BigDecimal ma5 = decimal(indicators == null ? null : indicators.ma5());
        BigDecimal ma20 = decimal(indicators == null ? null : indicators.ma20());
        BigDecimal macdHistogram = decimal(indicators == null ? null : indicators.macdHistogram());
        BigDecimal rsi = decimal(indicators == null ? null : indicators.rsi14());

        List<NewsDto> relatedNews = instrument == null
                ? List.of()
                : newsService.list(null, instrument.getSymbol(), null, 20).items();
        int positiveNews = (int) relatedNews.stream()
                .filter(n -> n.sentiment() == NewsSentiment.POSITIVE).count();
        int negativeNews = (int) relatedNews.stream()
                .filter(n -> n.sentiment() == NewsSentiment.NEGATIVE).count();

        PatternScoreCalculator.Inputs inputs = new PatternScoreCalculator.Inputs(
                signal.getScore(),
                volumeRatio,
                currentPrice != null && ma20 != null && currentPrice.compareTo(ma20) >= 0,
                ma5 != null && ma20 != null && ma5.compareTo(ma20) >= 0,
                macdHistogram != null && macdHistogram.signum() >= 0,
                rsi,
                averageRangePct,
                positiveNews,
                negativeNews);
        PatternScoreCalculator.Scores scores = PatternScoreCalculator.calculate(inputs, properties);

        return new Assessment(volumeRatio, averageRangePct, indicators, currentPrice, ma5, ma20,
                macdHistogram, rsi, positiveNews, negativeNews, scores);
    }

    private IndicatorSummary safeIndicators(PatternSignal signal) {
        try {
            return indicatorService.compute(signal.getInstrumentId(), signal.getTimeframe());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal volumeRatio(List<Candle> newestFirst) {
        if (newestFirst.size() < 2 || newestFirst.get(0).volume() == null) return null;
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = 1; i < newestFirst.size(); i++) {
            if (newestFirst.get(i).volume() != null) {
                sum = sum.add(newestFirst.get(i).volume());
                count++;
            }
        }
        if (count == 0 || sum.signum() == 0) return null;
        BigDecimal average = sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        return newestFirst.get(0).volume().divide(average, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal averageRangePct(List<Candle> candles) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Candle candle : candles) {
            if (candle.high() == null || candle.low() == null
                    || candle.close() == null || candle.close().signum() == 0) continue;
            sum = sum.add(candle.high().subtract(candle.low())
                    .divide(candle.close(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
            count++;
        }
        return count == 0 ? null
                : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private List<String> reasons(PatternSignal signal, BigDecimal volumeRatio,
                                 IndicatorSummary indicators, int positiveNews) {
        List<String> out = new ArrayList<>();
        out.add(signal.getType() + " 패턴 구조가 탐지 규칙을 충족했습니다.");
        if (signal.getStatus() == SignalStatus.NEAR_COMPLETION) {
            out.add("현재 가격이 패턴 완료 예상 구간에 근접했습니다.");
        }
        if (volumeRatio != null && volumeRatio.compareTo(BigDecimal.ONE) > 0) {
            out.add("최근 거래량이 직전 평균보다 " + value(volumeRatio, "배") + " 높습니다.");
        }
        BigDecimal macd = decimal(indicators == null ? null : indicators.macdHistogram());
        if (macd != null && macd.signum() > 0) out.add("MACD 모멘텀이 양수 구간입니다.");
        if (positiveNews > 0) out.add("관련 긍정 뉴스가 " + positiveNews + "건 감지되었습니다.");
        return out;
    }

    private List<String> risks(PatternSignal signal, BigDecimal rangePct,
                               IndicatorSummary indicators, int negativeNews,
                               SignalExplainDto.Confidence confidence) {
        List<String> out = new ArrayList<>();
        if (signal.getInvalidationPrice() != null) {
            out.add("가격이 " + value(signal.getInvalidationPrice(), "") + " 기준을 이탈하면 패턴이 무효화됩니다.");
        }
        BigDecimal rsi = decimal(indicators == null ? null : indicators.rsi14());
        if (rsi != null && (rsi.compareTo(BigDecimal.valueOf(70)) > 0
                || rsi.compareTo(BigDecimal.valueOf(30)) < 0)) {
            out.add("RSI " + value(rsi, "") + "로 과열 또는 과매도 구간 변동에 주의해야 합니다.");
        }
        if (rangePct != null && rangePct.compareTo(BigDecimal.TEN) > 0) {
            out.add("최근 봉 변동성이 높아 예상 체결가와 실제 체결가 차이가 커질 수 있습니다.");
        }
        if (negativeNews > 0) out.add("관련 부정 뉴스가 " + negativeNews + "건 포함되어 있습니다.");
        if ("INSUFFICIENT".equals(confidence.grade())) {
            out.add("동일 조건의 1일 성과 표본이 부족해 점수 신뢰도를 판단하기 어렵습니다.");
        }
        if (out.isEmpty()) out.add("확정 수익 신호가 아니며 시장 상황에 따라 패턴이 무효화될 수 있습니다.");
        return out;
    }

    private List<String> nextChecks(PatternSignal signal) {
        List<String> out = new ArrayList<>();
        if (signal.getCTarget() != null) out.add("현재가가 C 예상가에 도달한 뒤 지지되는지 확인하세요.");
        out.add("다음 확정봉의 종가와 거래량이 패턴 방향을 유지하는지 확인하세요.");
        out.add("무효화 가격과 최근 저점 이탈 여부를 함께 확인하세요.");
        return out;
    }

    private String riskGuard(PatternSignal signal, BigDecimal rangePct, IndicatorSummary indicators) {
        if (signal.getStatus() == SignalStatus.INVALIDATED) return "BLOCK";
        if (signal.getStatus() == SignalStatus.EXPIRED || indicators == null
                || (rangePct != null && rangePct.compareTo(BigDecimal.TEN) > 0)) return "WARN";
        return "PASS";
    }

    private SignalExplainDto.Confidence confidence(SummaryRow row) {
        if (row == null) return new SignalExplainDto.Confidence("INSUFFICIENT", 0, null, null, "1d");
        long n = row.sampleSize();
        String grade = n < 10 ? "INSUFFICIENT" : n < 30 ? "LOW" : n < 100 ? "MEDIUM" : "HIGH";
        return new SignalExplainDto.Confidence(grade, n, row.hitRate(), row.avgReturnPct(), row.horizon());
    }

    private String trendDetail(BigDecimal price, BigDecimal ma5, BigDecimal ma20,
                               BigDecimal macd, BigDecimal rsi) {
        return "현재가 " + relation(price, ma20) + " MA20 · MA5 " + relation(ma5, ma20)
                + " MA20 · MACD " + (macd == null ? "-" : macd.signum() >= 0 ? "양수" : "음수")
                + " · RSI " + value(rsi, "");
    }

    private String relation(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return "-";
        return left.compareTo(right) >= 0 ? "상단" : "하단";
    }

    private BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String value(BigDecimal value, String suffix) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffix;
    }
}
