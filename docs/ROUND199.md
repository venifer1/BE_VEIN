# VEIN Round 199

Date: 2026-09-13

## 주간 리포트 파서/부호표기 경계(WeeklyReportService.parse) 단위 테스트 (BE, 회귀 보호)

자율 루프 R199. R142에서 recoverHits/pct/signed는 덮었으나, 그 근간인 `parse`(잘못된
입력 → ZERO 기본값)와 그로 인한 `signed`의 미묘한 분기가 미검증이었다.

### 바뀐 것

- `WeeklyReportService.parse`: `private static` → package-private `static`(동작 무변경).
- 기존 `WeeklyReportServiceTest` +2:
  - `parse`: null/공백/`"abc"` → 0(ZERO 기본값), 숫자열 파싱.
  - **`signed` 미묘한 분기 확정**: 빈값은 `"0.00"`(부호 없음)이지만 잘못된 문자열은
    0으로 파싱돼 `"+0.00"`(부호 있음); 반올림으로 0이 되는 미세 음수(`-0.001`)도 `"+0.00"`.

### 검증

ASCII 경로 복사본 `gradlew test --tests WeeklyReportServiceTest` **BUILD SUCCESSFUL**
(6→8 @Test). 로직 무변경(가시성만 확장, 기존 동작을 테스트로 못박음).
