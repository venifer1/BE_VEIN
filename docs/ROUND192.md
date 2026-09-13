# VEIN Round 192

Date: 2026-09-13

## 데모 캔들 생성기(genCandles) 불변식 단위 테스트 (FE, 회귀 보호)

자율 루프 R192. 데모 차트의 근간인 시드 OHLC 생성기 `genCandles`는 무테스트였다. 차트가
깨져 보이는 대부분의 원인(OHLC 불일치·간격 어긋남)을 불변식으로 못박음.

### 바뀐 것

- `lib/mockData.test.ts` +4(`genCandles` 기존 export 사용, 코드 변경 0):
  - `count` 개수 반환(기본 200).
  - **OHLC 유효성**: 모든 캔들 high ≥ max(open,close) ≥ min ≥ low, high≥low, 거래량>0.
  - **연속성/균등간격**: 각 open = 직전 close, open_time 간격 일정(step).
  - 시드 결정성: 동일 종목+타임프레임 두 번 호출 시 종가 배열 동일(SSR/CSR 일치).

### 검증

FE `npx vitest run` **8파일 121개 그린**(R191 117 → +4).
