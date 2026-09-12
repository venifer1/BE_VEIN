# VEIN Round 73

Date: 2026-09-12

## 오프라인 데모(mock) 전 화면 검증 + 로그인 역할 일관성 (Track B)

자율 루프 R73. R71에서 mock 랜딩 갭을 고쳤고, 이번엔 **mock 모드 전 화면을 full smoke로 검증**했다.

### 검증

- `NEXT_PUBLIC_USE_MOCK=true`로 빌드해 포트 3100(라이브 3000 무간섭) 구동 후, **전체 smoke를
  mock 자격(`tester@vein.test`/`mock1234`)으로 실행 → 0에러**. 홈·스캐너(패턴/조건검색/청산 탭)·
  데이터 서브탭·뉴스·관리·설정·신호상세·온보딩·다이제스트·구독·합성값 배너까지 mock 핸들러가
  모두 온전함을 확인(오프라인 데모 end-to-end 정상).

### 바뀐 것 (FE mock 일관성)

- mock 로그인/refresh/`/me`가 `role: "TESTER"`를 반환했는데 `mockUsers.usr_1`은 `SUPER_ADMIN`이라
  불일치 → 데모에서 로그인 사용자가 관리 기능을 못 보는 문제. 셋 다 **`SUPER_ADMIN`으로 일관화**
  (라이브 데모 계정과 동일하게 관리 화면까지 노출). 신규 signup 사용자는 `TESTER` 유지(정상).

### 검증(라이브)

- 프론트 `typecheck`/`build` 통과. 라이브 3000 재기동 후 **Chrome smoke 0에러**. 백엔드/계약 무변경.
