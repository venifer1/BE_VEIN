# VEIN Round 72

Date: 2026-09-12

## Admin 운영 개요에 구독 티어(FREE/PRO) 집계 (Track C 운영)

자율 루프 R72. 티어(R56)·엔타이틀먼트(R60)가 실사용화됐는데 **운영자가 FREE/PRO 분포를 볼 수
없었다**. Admin 개요에 티어 집계를 더해 수익화 현황을 한눈에 본다.

### 바뀐 것

- **백엔드** `AdminController` `UserSummary`에 `byTier`(Map) 추가 —
  `countBy(users, u -> Entitlements.normalize(u.getTier()))`. `GET /admin/overview`의 users 블록에 노출.
- **프론트** admin: "사용자" StatCard 부제에 `PRO N` 추가, "구독 티어" 분포 카드 추가. 타입
  `AdminOverview.users.by_tier?`. mock overview도 `by_tier` 반영.

### 검증

- 백엔드 `compileJava` 성공. 프론트 `typecheck`/`build` 통과.
- **라이브**: `GET /admin/overview` → `users.by_tier = {FREE: 2}`(현재 전원 FREE). **Chrome smoke
  0에러**.
