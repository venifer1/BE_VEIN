# VEIN Round 152

Date: 2026-09-13

## 데모 백테스트 파이프라인(runBacktest) 결정성·불변식 테스트 (FE, 회귀 보호)

자율 루프 R152. R146에서 지표 집계 부품(`metricsFromTrades`)은 덮었으나, 그것을 감싸
전체 데모 백테스트를 합성하는 `runBacktest`(파라미터 시드로 SSR/CSR 안정)는 무테스트였다.
결정성·구조 불변식·워크포워드 분기를 못박음.

### 바뀐 것

- `lib/mockData.test.ts` +4(`runBacktest` 기존 export 사용, 코드 변경 0):
  - **결정성**: 동일 파라미터 → metrics 완전 동일 + 체결 수익률 배열 동일(시드 안정).
  - 구조 불변식: 체결 28~35, `metrics.trade_count`=체결수, `equity_curve` 길이=체결+1
    (1.0 시드 + 체결당 1), best ≥ worst.
  - 파라미터 에코+기본값(target 5·stop 3·fee 0.1), 미요청 시 `walk_forward` null.
  - 워크포워드 요청 시 IS/OOS 블록 생성·`overfit_warning` boolean·`is_ratio` [0.5,0.9]
    클램프(기본 0.70, 0.2→0.50, 0.99→0.90).

### 검증

FE `npx vitest run` **7파일 90개 그린**(R151 86 → +4). `mockData` 순수부 누적 22개.
