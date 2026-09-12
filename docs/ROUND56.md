# VEIN Round 56

Date: 2026-09-12

## 구독 티어 실사용화 — 한도 실집행 + admin 티어 설정 (Track C)

R52가 티어(FREE/PRO)·엔타이틀먼트·게이트 1개(저장식)를 얹었지만 **미완결**이 둘 있었다:
(1) 구독 카드가 "알림 규칙 10개" 한도를 광고하는데 **실제로 집행 안 됨**, (2) **PRO로 올릴
방법 자체가 없음**(결제 후속). 이 둘을 채워 티어 시스템을 실제로 동작하게 만든다.

### 추가·변경

- **알림 한도 실집행** (`AlertService.create`): FREE는 알림 규칙 `alertLimit`(10) 초과 시
  **402 PLAN_LIMIT_EXCEEDED**. PRO(-1)는 무제한. `EntitlementsService.alertLimit` +
  `AlertRepository.countByUserId`. 중복(DUPLICATE) 체크 뒤에 게이트.
- **admin 티어 설정** (`PATCH /api/v1/admin/users/{id}/tier` body `{tier}`): FREE/PRO 검증
  (그 외 400), `User.updateTier`, 감사 로그 `USER_TIER_UPDATE`(from/to). 결제 없이 운영자가
  PRO를 부여/회수할 수 있어 티어가 실제로 쓸 수 있게 됨.
- `UserDto`에 `tier` 추가 → admin 목록/토글에서 노출.
- **FE**: admin 사용자 카드에 티어 배지(FREE/PRO) + "PRO로 / FREE로" 토글(`useAdminSetTier`).
  구독 카드(R52)는 `/me/entitlements`가 티어에서 파생하므로 승급 즉시 PRO로 반영. mock 계약
  동형(users에 tier, `/tier` PATCH 핸들러).

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. 프론트 `typecheck`/`build` 통과.
- **라이브**:
  - `PATCH /admin/users/{id}/tier` PRO → `tier=PRO`, `/me/entitlements` `pro=true`. FREE 복귀,
    잘못된 값(`gold`) → 400.
  - 알림 한도: FREE로 10개 보유 상태에서 11번째 생성 → **402 PLAN_LIMIT_EXCEEDED**(한글 안내).
    PRO 승급 후 재시도 → **201**(무제한). 검증 후 테스트 알림 정리·FREE 복귀.
  - **Chrome smoke 0에러**.

### 남은 것

- 실제 결제(PG)·자동 PRO 승급·약관/개인정보/투자고지 정식화는 여전히 후속. 이번으로 "티어를
  수동으로 부여하고 한도가 실제 동작하는" 상태까지 완성.
