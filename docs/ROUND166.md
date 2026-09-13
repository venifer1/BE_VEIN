# VEIN Round 166

Date: 2026-09-13

## 파생 목록 정렬 보조(DerivativesService priorityIndex/plain/num) 단위 테스트 (BE, 회귀 보호)

자율 루프 R166. 파생(선물) 목록 정렬의 보조 순수 함수 3종 — `priorityIndex`(메이저
30종 우선순위)·`plain`(BigDecimal→plain string)·`num`(안전 파싱) — 이 무테스트였다.

### 바뀐 것

- 위 3개: `private static` → package-private `static`(동작 무변경).
- 신규 `DerivativesServiceTest` (+3):
  - `priorityIndex`: BTC=0·ETH=1·TIA=29, 미등재 → MAX_VALUE, 대문자 목록이라 소문자
    미매칭.
  - `plain`: null→null, 지수표기 방지(1E-8→0.00000001).
  - `num`: null/무효 → **NEGATIVE_INFINITY**(무효값을 정렬 맨 뒤로; 김치 num의 0과 다름을
    명시), 정상 파싱.

### 검증

ASCII 경로 복사본 `gradlew test --tests DerivativesServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
