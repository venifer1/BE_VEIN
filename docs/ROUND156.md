# VEIN Round 156

Date: 2026-09-13

## 모의투자 금액/수량/수익률 표기(PaperTradingService) 단위 테스트 (BE, 회귀 보호)

자율 루프 R156. 모의투자 응답의 숫자 필드(계약상 String)를 만드는 표기 헬퍼
`money`·`qty`(소수 8자리)·`pct`(소수 4자리) — 모두 HALF_UP 후 후행 0 제거 — 는
순수 로직인데 테스트가 없었다.

### 바뀐 것

- `PaperTradingService.money`·`qty`·`pct`: `private static` → package-private `static`
  (동작 무변경).
- 신규 `PaperTradingServiceTest` (+4):
  - `money`: 8자리 스케일+후행0 제거(100.50000000→100.5), 정수(2), 최소단위(0.00000001),
    초과분 HALF_UP(1.123456789→1.12345679).
  - `qty`: money와 동일 동작 확인.
  - `pct`: 4자리+후행0 제거(10.2500→10.25), 음수(-5.5), HALF_UP(0.33335→0.3334).
  - **0은 "0"으로 렌더**(Java 21 `stripTrailingZeros(0)`→scale0 확인, 옛 "0E-8" 버그 없음).

### 검증

ASCII 경로 복사본 `gradlew test --tests PaperTradingServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
