# VEIN Round 124

Date: 2026-09-13

## compactUsd 단일 소스화 + 테스트 (FE, dedup·회귀 보호)

자율 루프 R124. USD 대형 금액 축약 함수 `compactUsd`가 **3개 페이지에 중복 정의**
(data·derivatives·instruments 상세)돼 드리프트 위험이 있었다(R70/R99 유형). 단일 소스로
추출 + 테스트.

### 바뀐 것 (프론트 전용)

- `lib/format.ts`에 `export compactUsd`(음수 안전 버전: `Math.abs`+`Number.isFinite`) 추가.
  data·derivatives·instruments의 로컬 정의 3개 제거 → 공유 import.
  ※ 홈의 `compactUsd`(₩ 조/억/만)·`compactUsdScaled`($ 조/억)는 **다른 함수**라 유지(오해로
  통합하지 않음 — R108 교훈).
- `lib/format.test.ts`에 compactUsd 3케이스(T/B/M·locale grouping·음수/null/garbage) 추가.

### 검증

`vitest run` **21/21 그린**(format 12·types 5·api 4). `tsc`·`next build` 통과. 3개 페이지의
축약 표시 동작 동일(양수 경로 불변, 음수도 안전).
</content>
