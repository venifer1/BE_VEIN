# VEIN Round 131

Date: 2026-09-13

## fmtNum lib 추출 + 테스트 (FE)

자율 루프 R131. 홈에서 8회 쓰이는 로컬 `fmtNum`(ko-KR 숫자 포맷)을 `lib/format.ts`로
추출해 재사용·테스트 가능하게 정리.

### 바뀐 것 (프론트 전용)

- `lib/format.ts`에 `export fmtNum` 추가, 홈 로컬 정의 제거 → import.
- `lib/format.test.ts`: 6케이스(그룹핑·소수 캡·정수·null/빈값→"-"·비수치 passthrough).

### 검증

`vitest run` **37/37 그린**. `tsc`·`next build` 통과. 동작 동일.

_비고: 홈/paper의 숫자·통화 포맷 로컬 헬퍼(compactUsd·compactUsdScaled·koreanMoney·fmtNum)를
R124·R127·R129·R131로 lib/format에 중앙화 완료._
</content>
