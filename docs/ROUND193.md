# VEIN Round 193

Date: 2026-09-13

## 데모 지표 요약(getIndicators) 관계식 단위 테스트 (FE, 회귀 보호)

자율 루프 R193. 종목 상세의 데모 지표 요약 `getIndicators`(RSI·MA·볼린저·MACD)는
무테스트였다. 밴드 정렬·MACD 히스토그램 항등 등 관계식을 못박음.

### 바뀐 것

- `lib/mockData.test.ts` +2(`getIndicators` 기존 export 사용, 코드 변경 0):
  - timeframe 에코 + **볼린저 중앙선 = MA20**, 상단 ≥ 중앙 ≥ 하단.
  - RSI 데모 밴드 [45,65), **MACD 히스토그램 = MACD − 시그널**(항등).

### 검증

FE `npx vitest run` **8파일 123개 그린**(R192 121 → +2).
