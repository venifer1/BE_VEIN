# VEIN Round 170

Date: 2026-09-13

## 시장 데이터 신선도 판정(MarketController.freshness) 단위 테스트 (BE, 회귀 보호)

자율 루프 R170. 시장 데이터 응답의 FRESH/DELAYED 배지를 결정하는 `freshness(collectedAt,
window)`는 사용자가 "지금 값인가 지연된 값인가"를 판단하는 근거인데 테스트가 없었다.

### 바뀐 것

- `MarketController.freshness`: `private static` → package-private `static`(동작 무변경).
- 신규 `MarketControllerTest` (+3):
  - collectedAt null → DELAYED(수집 이력 없음).
  - 최근(now, 창 1h) → FRESH.
  - 창 초과(2시간 전, 창 1h) → DELAYED.

### 검증

ASCII 경로 복사본 `gradlew test --tests MarketControllerTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장). now() 사용부는 큰 창(시간 단위)으로 결정성 확보.
