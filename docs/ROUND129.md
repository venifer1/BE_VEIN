# VEIN Round 129

Date: 2026-09-13

## compactUsdScaled lib 추출 + 테스트 (FE)

자율 루프 R129. 홈 로컬 `compactUsdScaled`(USD를 한글 조/억/만 스케일로, 예 "$3.42조")를
`lib/format.ts`로 추출해 재사용·테스트 가능하게 정리(compactUsd 계열과 함께 중앙화).

### 바뀐 것 (프론트 전용)

- `lib/format.ts`에 `export compactUsdScaled` 추가, 홈 로컬 정의 제거 → import.
- `lib/format.test.ts`: 5케이스(조/억/만·<1만 grouping·음수·null/garbage).

### 검증

`vitest run` **35/35 그린**. `tsc`·`next build` 통과. 동작 동일.
</content>
