# VEIN Round 52

Date: 2026-09-12

## Track C 착수 — 구독 티어 + 엔타이틀먼트 (수익화 토대)

Track A·B(내가 쓰기 / 남에게 보여주기)가 대체로 마감돼, 사용자 결정에 따라 Track C(상용화)를
**착수**한다. 결제 연동·복잡한 기능 제한은 컴플라이언스/운영 리스크가 커서 후속으로 미루고,
이번엔 **티어(FREE/PRO) + 엔타이틀먼트(기능/한도) + 게이트 1개**로 토대만 얹는다(비파괴적).

### 추가 (`com.vein.billing`)

- **V28** `users.tier VARCHAR(16) NOT NULL DEFAULT 'FREE'`. 기존/신규 모두 FREE.
- `Entitlements`(순수) — 티어→한도 매핑 한 곳: `scannerRuleLimit`(FREE 3 / PRO -1=무제한),
  `alertLimit`(FREE 10 / PRO -1), `normalize`(미지/누락→FREE), `forTier`(DTO 조립).
- `EntitlementsDto.Status(tier, pro, features[])` / `Feature(key, label, limit)`.
- `EntitlementsService` — 사용자 티어 조회 + 엔타이틀먼트/한도 파생(게이트 재사용).
- `GET /api/v1/me/entitlements` → 현재 티어와 기능/한도.
- `User.tier` 필드(기본 "FREE").

### 게이트 1개 (엔드투엔드 실증)

- 조건검색식 저장(`ConditionScannerService.save`)에 FREE 한도 게이트: 현재 저장식 수 ≥ 한도면
  **402 PLAN_LIMIT_EXCEEDED**(`ErrorCode` 추가, `ScannerRuleRepository.countByUserId`). PRO(-1)는
  무제한. 저장 경로에만 적용(수정/실행 무관), 4개째부터 막혀 기존 흐름 비파괴.

### 프론트

- `useEntitlements` + `Entitlements` 타입. 설정 "구독" 카드: 티어 배지(FREE/PRO) + 기능별 한도
  (무제한/N개) + "PRO 업그레이드 (준비 중)"(결제 후속이라 비활성). mock 계약 동형(FREE).

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `EntitlementsTest` 5/5**(FREE/PRO 한도·
  정규화·대소문자·feature 노출). ASCII 경로 **BUILD SUCCESSFUL**.
- 프론트 `typecheck`/`build` 통과.
- **라이브**(bootRun + V28 적용):
  - `GET /me/entitlements` → FREE, `SAVED_SCANNER_RULES=3`·`ALERTS=10`.
  - 저장식 게이트: 1~3번째 201 저장, **4번째 402 PLAN_LIMIT_EXCEEDED**(한글 안내 메시지).
  - **Chrome smoke 0에러**(신규 구독 카드 체크). 설정 "구독" 카드 렌더 스크린샷 육안.
  - 검증 후 임시 유저·저장식 정리로 DB 원복.

### 남은 것(후속 라운드 후보)

- 결제 연동(PG·컴플라이언스)·PRO 승급 플로우·기능 제한 전면 적용(알림/백테스트/디지트 창 등)·
  약관/개인정보/투자고지 정식화. 이번엔 **토대만** — 이후는 별도 결정/라운드.
