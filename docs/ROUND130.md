# VEIN Round 130

Date: 2026-09-13

## 홈 죽은 코드 제거 — 미사용 ₩ compactUsd (FE, 정리)

자율 루프 R130. 홈 페이지에 `compactUsd`(이름과 달리 ₩ 조/억/만을 반환)가 정의만 있고
호출부가 없는 **죽은 코드**였다(글로벌 시총 표시는 R129의 `compactUsdScaled`가 담당).
제거해 혼란(₩인데 이름은 Usd)과 미사용 코드를 정리.

### 바뀐 것 (프론트 전용)

- `app/page.tsx`에서 미사용 `compactUsd`(₩ 버전) 함수 삭제.

### 검증

`tsc`(unused 참조 없음 확인)·`next build` 통과, `vitest run` 35/35 그린. 삭제만(동작 무변경).
</content>
