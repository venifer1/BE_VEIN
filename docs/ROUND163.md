# VEIN Round 163

Date: 2026-09-13

## 모의투자 주문 입력 정규화(PaperTradingService normalize*·leverage) 단위 테스트 (BE, 회귀 보호)

자율 루프 R163. 모의투자 주문 생성의 입력 검증(기본값·대소문자·트림·허용값 검사)은
`normalizeSide`·`normalizeType`·`normalizeInvestmentType`·`normalizePositionSide`·
`leverage`에 몰려 있는데 테스트가 없었다. 잘못된 입력이 400 대신 500으로 새거나 기본값이
바뀌면 사용자에게 바로 드러나는 지점.

### 바뀐 것

- 위 5개 메서드: `private static` → package-private `static`(동작 무변경).
- 기존 `PaperTradingServiceTest` +5:
  - `normalizeSide`: null/공백→BUY, " sell "→SELL(트림·대문자), 미지원값 → ApiException.
  - `normalizeType`: null→MARKET, limit→LIMIT, 미지원 → ApiException.
  - `normalizeInvestmentType`: null→SPOT, futures→FUTURES, 미지원 → ApiException.
  - `normalizePositionSide`: 현물이면 항상 null, 선물+미지정은 side로 유추(BUY→LONG/
    SELL→SHORT), 선물+명시 대소문자 무시, 미지원 → ApiException.
  - `leverage`: 현물은 1 고정, 선물 기본 1, 50x 경계 허용, **51x 초과 거부**(ApiException).

### 검증

ASCII 경로 복사본 `gradlew test --tests PaperTradingServiceTest` **BUILD SUCCESSFUL**
(4→9 @Test). 로직 무변경(가시성만 확장).
