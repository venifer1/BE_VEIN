package com.vein.signal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.explain.SignalExplainService;

import lombok.extern.slf4j.Slf4j;

/**
 * 활성 신호(DETECTED / NEAR_COMPLETION)의 종합 Pattern Score를 계산해
 * {@code pattern_signals.pattern_score}에 영속화한다(R90).
 *
 * <p>왜 필요한가: 홈 "오늘의 주목 신호"(`/signals/top`)는 구조 점수(탐지기 완성도)만으로
 * 정렬했는데, 실제 큐레이션 가치는 완성도에 거래량·추세·변동성·뉴스를 합산한 종합 점수
 * (Explain의 Pattern Score)에 있다. 그 점수를 매 top() 호출마다 신호별로 계산하면(캔들·지표·
 * 뉴스 조회) 홈 로드가 무거워지므로, <b>주기적으로 미리 계산해 저장</b>하고 top()은 저장값으로
 * 정렬한다. 미계산 신호는 top 정렬에서 {@code score}로 폴백된다.
 *
 * <p>지표·뉴스는 시간에 따라 변하므로 활성 신호는 주기적으로 재계산한다(스케줄러). 탐지 직후
 * 값이 비어 있어도 다음 주기/기동 시 채워진다 — 스캔 핫패스와 분리(성과 추적과 동일 철학).
 */
@Service
@Slf4j
public class SignalPatternScoreService {

    private static final List<SignalStatus> ACTIVE =
            List.of(SignalStatus.DETECTED, SignalStatus.NEAR_COMPLETION);

    private final PatternSignalRepository signalRepository;
    private final SignalExplainService explainService;

    public SignalPatternScoreService(PatternSignalRepository signalRepository,
                                     SignalExplainService explainService) {
        this.signalRepository = signalRepository;
        this.explainService = explainService;
    }

    /**
     * 모든 활성 신호의 pattern_score를 재계산·저장한다. 신호 하나가 실패해도 배치 전체가
     * 중단되지 않도록 per-signal try/catch. 값이 바뀐 행만 저장(멱등).
     *
     * @return 갱신된 신호 수.
     */
    @Transactional
    public int refreshActive() {
        int written = 0;
        for (PatternSignal signal : signalRepository.findByStatusIn(ACTIVE)) {
            try {
                BigDecimal next = BigDecimal.valueOf(explainService.computeScore(signal));
                BigDecimal cur = signal.getPatternScore();
                if (cur == null || cur.compareTo(next) != 0) {
                    signal.setPatternScore(next);
                    signalRepository.save(signal);
                    written++;
                }
            } catch (RuntimeException e) {
                log.warn("pattern-score: signal {} failed: {}", signal.getId(), e.getMessage());
            }
        }
        if (written > 0) {
            log.info("pattern-score: wrote/updated {} rows", written);
        }
        return written;
    }
}
