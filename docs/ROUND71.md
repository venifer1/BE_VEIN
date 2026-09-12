# VEIN Round 71

Date: 2026-09-12

## 오프라인 데모(mock 모드) landing 갭 수정 (Track B)

자율 루프 R71. 26라운드 동안 mock 어댑터에 신규 엔드포인트를 계속 동기화해 왔는데, **정적 대조
(queries.ts가 호출하는 경로 vs mockAdapter 핸들러)** 로 누락 2건을 발견했다:

- `POST /auth/signup` (R35 공개 회원가입) — mock 핸들러 없음
- `GET /public/reports/weekly` (R34 공개 주간 리포트) — mock 핸들러 없음

둘 다 **비로그인 landing(`/landing`)** 이 쓴다. 즉 `NEXT_PUBLIC_USE_MOCK=true`(백엔드 없이 보여주는
Track B 데모)에서 랜딩의 리포트·가입폼이 깨져 있었다.

### 추가 (FE mock)

- `mockAdapter`에 `/public/reports/weekly`(WeeklyReport 계약: overall·rows·highlights·disclaimer)와
  `/auth/signup`(SignupResponse: auto-approve로 토큰 발급, `password<8` → 400) 핸들러 추가.

### 검증

- 프론트 `typecheck` 통과(계약 형태 일치).
- **실제 mock 빌드 검증**: `NEXT_PUBLIC_USE_MOCK=true`로 빌드해 별도 포트(3100, 라이브 3000 무간섭)에서
  구동 → `/landing` 리포트(적중률)·가입폼 렌더, mock 로그인(mock1234)→홈(터미널) 정상, **pageerror 0**.
- 라이브(3000) 재빌드·재기동 후 **Chrome smoke 0에러**. 백엔드/계약 무변경(순수 mock 보강).

### 운영 메모

- 실행 중인 `next start` 아래에서 `npm run build`를 하면 청크 해시가 갈려 running 서버가 stale 청크
  404를 낸다 → 재빌드 후 반드시 FE 재기동. (라이브 코드는 mockAdapter 변경에 무영향이라 live 동작엔
  변화 없음.)
