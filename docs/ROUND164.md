# VEIN Round 164

Date: 2026-09-13

## 속보 조회 파라미터 정규화(NewsService normalizeSource/Symbol/clampSize) 단위 테스트 (BE, 회귀 보호)

자율 루프 R164. 속보(뉴스) 목록 조회의 입력 정규화 `normalizeSource`(TELEGRAM/BLOOMBERG
검증)·`normalizeSymbol`(대문자/트림)·`clampSize`(페이지 크기 [1,100] 클램프)는 순수
로직인데 테스트가 없었다.

### 바뀐 것

- 위 3개: `private static` → package-private `static`(동작 무변경).
- 신규 `NewsServiceTest` (+3):
  - `normalizeSource`: null/공백→null, 트림·대문자, 미지원 소스 → ApiException.
  - `normalizeSymbol`: null/공백→null, " krw-btc "→"KRW-BTC".
  - `clampSize`: null→20(기본), 0/-10→1(하한), 100 경계, 1000→100(상한).

### 검증

ASCII 경로 복사본 `gradlew test --tests NewsServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
