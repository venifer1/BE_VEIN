# VEIN Round 171

Date: 2026-09-13

## 틱띄기 체결 방향 분류(ScalpSidecarClient isBuy/isAsk) 단위 테스트 (BE, 회귀 보호)

자율 루프 R171. R155에서 `ratio`는 덮었고, 오더북/체결 파싱이 매수·매도를 나누는 휴리스틱
`isBuy`(B*/BID)·`isAsk`(A*/S*/SELL)는 아직 무테스트였다. 사이드카가 보내는 다양한 side
표기(대소문자·약어)를 흡수하는 지점이라 회귀 보호.

### 바뀐 것

- `isBuy`·`isAsk`: `private static` → package-private `static`(동작 무변경).
- 기존 `ScalpSidecarClientTest` +2:
  - `isBuy`: buy/BID/bid → true, sell/ask → false.
  - `isAsk`: ask(A*)·SELL·sell/short(S*) → true, buy/bid → false.

### 검증

ASCII 경로 복사본 `gradlew test --tests ScalpSidecarClientTest` **BUILD SUCCESSFUL**
(3→5 @Test). 로직 무변경(가시성만 확장).
