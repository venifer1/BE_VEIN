# VEIN Round 198

Date: 2026-09-13

## 거시경제 지표 표기 헬퍼(MacroService parse/plain/signed) 단위 테스트 (BE, 회귀 보호)

자율 루프 R198. R132에서 국면 판정 `label`은 덮었고, 거시 지표 표기 보조 순수 함수
`parse`(안전 파싱)·`plain`(null-safe)·`signed`(부호 표기)는 아직 무테스트였다.

### 바뀐 것

- `MacroService.parse`·`plain`·`signed`: `private static` → package-private `static`
  (동작 무변경).
- 기존 `MacroServiceTest` +3:
  - `parse`: null/공백/`"abc"` → null, 숫자열 파싱.
  - `plain`: null → null, 지수표기 방지(1E-8 → 0.00000001).
  - `signed`: 음수 아니면 `+` 선행(1.5→"+1.5", 0→"+0", -3→"-3").

### 검증

ASCII 경로 복사본 `gradlew test --tests MacroServiceTest` **BUILD SUCCESSFUL**
(6→9 @Test). 로직 무변경(가시성만 확장).
