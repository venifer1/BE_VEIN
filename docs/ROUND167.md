# VEIN Round 167

Date: 2026-09-13

## TVL 조회 모드 정규화(TvlService normalizeMode/plain) 단위 테스트 (BE, 회귀 보호)

자율 루프 R167. TVL(디파이 예치금) 조회의 `normalizeMode`(PROTOCOL/CHAIN 검증)·
`plain`(BigDecimal→plain string) 순수 로직이 무테스트였다.

### 바뀐 것

- 위 2개: `private static` → package-private `static`(동작 무변경).
- 신규 `TvlServiceTest` (+2):
  - `normalizeMode`: null/공백→PROTOCOL(기본), " chain "→CHAIN(트림·대문자),
    미지원 → ApiException.
  - `plain`: null→null, 지수표기 방지(1E9→1000000000).

### 검증

ASCII 경로 복사본 `gradlew test --tests TvlServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
