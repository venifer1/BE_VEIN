# VEIN Round 160

Date: 2026-09-13

## 전체 스위트 통합 헬스체크 + 커버리지 성장 확인 (BE/FE, 마일스톤)

자율 루프 R160(마일스톤). R143 이후 17라운드(R144~R159) 동안 FE mock 순수부와 BE
순수 산출식 테스트를 대거 추가했으므로, 전체 스위트를 한 번에 돌려 통합 통과·회귀
없음을 확인.

### 결과

- 백엔드 전체 `gradlew test` (ASCII 경로) **BUILD SUCCESSFUL** — **테스트 파일 33개 ·
  @Test 155개** (R143 28파일·127 → **+5파일·+28**).
  - 신규 파일: Supply(circulatingPct)·SignalPerformance(pct)·ScalpSidecarClient(ratio)·
    PaperTrading(money/qty/pct)·SignalExplain(averageRangePct·volumeRatio).
  - 기존 확장: ConditionScanner(volumeRatio +4).
- 프론트 `vitest run` **7파일 90개** (R143 47 → **+43**).
  - 신규/확장: indicators(sma/bollinger)·mockData(classifyFg·metricsFromTrades·getMovers·
    getFearGreedHistory·runBacktest)·mockAdapter(toCho/isChoQuery/num/trimNum/idMatches/
    parseUrl/body).

### 결론

이번 세션 테스트 커버리지 누적 성장: **BE 127→155(+28), FE 47→90(+43)**. 차트 오버레이·
데모 파이프라인·핵심 순수 산출식(유통량·성과수익률·변동성·거래량비율·표기헬퍼)이 회귀
보호됨. 코드 로직 변경 없음(가시성 확장·테스트 전용 라운드들).
