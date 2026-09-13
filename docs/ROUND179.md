# VEIN Round 179

Date: 2026-09-13

## 주식 심볼 접미사 제거(EquityInstrumentSyncService.stripSuffix) 단위 테스트 (BE, 회귀 보호)

자율 루프 R179. 주식 종목 동기화가 provider 심볼("005930.KS")에서 거래소 접미사를 떼
내부 심볼("005930")로 만드는 `stripSuffix`는 순수 로직인데 테스트가 없었다.

### 바뀐 것

- `EquityInstrumentSyncService.stripSuffix`: `private static` → package-private `static`
  (동작 무변경).
- 신규 `EquityInstrumentSyncServiceTest` (+3):
  - 접미사 제거(005930.KS→005930, 035420.KQ→035420).
  - 점 없으면 그대로(AAPL, 빈문자).
  - **첫 점에서 자름**: BRK.B→BRK(티커 점 주의), 선행 점 → 빈문자.

### 검증

ASCII 경로 복사본 `gradlew test --tests EquityInstrumentSyncServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
