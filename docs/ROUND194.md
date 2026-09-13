# VEIN Round 194

Date: 2026-09-13

## 데모 캔들 캐시(getCandles) 메모이제이션 단위 테스트 (FE, 회귀 보호)

자율 루프 R194. `getCandles`는 `instrumentId:timeframe` 키로 생성 캔들을 캐시(candleStore)해
동일 세션에서 재사용하는데, 이 메모이제이션 계약이 무테스트였다. (지표·신호가 같은 캔들을
공유하므로 캐시 키가 어긋나면 값 불일치가 생긴다.)

### 바뀐 것

- `lib/mockData.test.ts` +3(`getCandles` 기존 export 사용, 코드 변경 0):
  - 200개 캔들 반환.
  - **동일 종목+타임프레임 → 같은 배열 참조**(캐시 히트).
  - 타임프레임별 분리 키(1d vs 4h → 다른 배열).

### 검증

FE `npx vitest run` **8파일 126개 그린**(R193 123 → +3).
