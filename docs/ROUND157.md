# VEIN Round 157

Date: 2026-09-13

## 평균 고저폭 변동성(SignalExplainService.averageRangePct) 단위 테스트 (BE, 회귀 보호)

자율 루프 R157. Explain의 변동성 점수 근거인 평균 고저폭
`averageRangePct = avg((high-low)/close*100)`은 순수 캔들 집계인데 테스트가 없었다.

### 바뀐 것

- `SignalExplainService.averageRangePct`: `private` → package-private `static`
  (`this` 미사용 확인, 동작 무변경).
- 신규 `SignalExplainServiceTest` (+4):
  - 캔들별 고저폭% 평균(20%+10% → 15.00).
  - close=0/null 캔들 제외(중간에 close=0 껴도 15.00).
  - 유효 캔들 0개(빈 리스트·close=0만) → null.
  - 2자리 반올림(0.5/100.3*100=0.498504 → 0.50).

### 검증

ASCII 경로 복사본 `gradlew test --tests SignalExplainServiceTest` **BUILD SUCCESSFUL**.
작성 중 `Candle` import 패키지 오인(`ingestion`→실제 `market.candle`) 컴파일 에러 1건을
즉시 잡아 수정. 로직 무변경.
