# VEIN Round 138

Date: 2026-09-13

## decimalString·ratioPct lib 추출 + 테스트 (FE)

자율 루프 R138. 모의투자 로컬 순수 헬퍼 `decimalString`(수량 후행0 제거)·`ratioPct`
(ROE 등 비율%)를 `lib/format.ts`로 추출해 재사용·테스트 가능하게 정리.

### 바뀐 것 (프론트 전용)

- `lib/format.ts`에 `decimalString`·`ratioPct` 추가, paper 로컬 정의 제거 → import.
- `lib/format.test.ts`: decimalString 6케이스(후행0·정수·0·비수치), ratioPct 6케이스
  (정상·분모0·null).

### 검증

`vitest run` **41/41 그린**. `tsc`·`next build` 통과. 동작 동일.
</content>
