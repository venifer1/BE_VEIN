# VEIN Round 177

Date: 2026-09-13

## 신호 성과 월 버킷 헬퍼(SignalPerformanceService isMonthBucket/monthOf) 단위 테스트 (BE, 회귀 보호)

자율 루프 R177. R154에서 `pct`는 덮었고, 월별 성과 집계의 버킷 판정 `isMonthBucket`·
UTC 월 산출 `monthOf`(yyyy-MM)는 아직 무테스트였다. 특히 UTC 경계 처리는 KST 환경에서
착각하기 쉬운 지점([[vein-legacy-engine-params]] KST→UTC 함정 참고).

### 바뀐 것

- 두 메서드: `private static` → package-private `static`(동작 무변경).
- 기존 `SignalPerformanceServiceTest` +3:
  - `isMonthBucket`: MONTH·" month "(대소문자·트림) → true, WEEK·빈·null → false.
  - `monthOf`: UTC yyyy-MM 포맷, null → null.
  - **UTC 경계**: 8/31 23:59Z → 2026-08, 9/1 00:00Z → 2026-09(로컬 아닌 UTC 기준).

### 검증

ASCII 경로 복사본 `gradlew test --tests SignalPerformanceServiceTest` **BUILD SUCCESSFUL**
(4→7 @Test). 로직 무변경(가시성만 확장).
