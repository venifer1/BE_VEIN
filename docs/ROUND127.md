# VEIN Round 127

Date: 2026-09-13

## koreanMoney 헬퍼 lib 추출 + 테스트 (FE, 회귀 보호)

자율 루프 R127. (사용자가 "얇은 라운드 OK, 3분마다 진행" 명시.) 모의투자 화면의 로컬
`koreanMoney`(억/만원 포맷)를 `lib/format.ts`로 추출해 재사용·테스트 가능하게 정리.

### 바뀐 것 (프론트 전용)

- `lib/format.ts`에 `export koreanMoney` 추가, paper 페이지 로컬 정의 제거 → import.
- `lib/format.test.ts`: koreanMoney 6케이스(1,000만원·1억 5,000만원·1억원·1만 미만/0/NaN→빈문자).

### 검증

`vitest run` **28/28 그린**(format 16·types 8·api 4). `tsc`·`next build` 통과. 동작 동일.
</content>
