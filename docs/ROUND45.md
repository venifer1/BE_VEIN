# VEIN Round 45

Date: 2026-09-12

## 알림 다이제스트(읽기 시점 요약) — Track A #3 (알림 노이즈 완화)

R41 "오늘의 주목 신호"가 **신호 과다**를, R42 "조용한 시간"이 **알림 타이밍**을
다뤘다면, R45는 **읽기 시점 요약**으로 "자리를 비운 사이 뭐가 왔나"를 한 줄로 준다.
저장 데이터를 바꾸지 않는 순수 집계 조회다.

### 추가된 것 (`com.vein.notification`)

- `GET /api/v1/notifications/digest?window=24` — 인증 사용자 스코프. `window`(시간, 기본 24 ·
  최대 168, 1 미만은 1로 클램프) 창 안의 알림을 분류·집계하고 안읽은 최신 표본과 요약을 반환.
- `NotificationDigest` — **순수 정적 집계기**(DB/컨텍스트 불필요 → 단위 테스트 용이).
  스키마에 type 컬럼이 없으므로 분류를 **구조 컬럼 + 안정적 영문 제목 마커**에서 유도한다:
  - `signalId != null` → **SIGNAL**(알림규칙이 발생시킨 패턴 신호)
  - 제목 `"Scanner match:"` → **SCANNER**(조건검색 저장식 매칭, `ConditionScannerService` 상수)
  - 제목 `"Liquidation spike"` → **LIQUIDATION**(청산 급증, `LiquidationService` 상수)
  - 그 외 → **SYSTEM**
  - 카테고리는 건수>0인 것만, 고정 우선순위(SIGNAL·SCANNER·LIQUIDATION·SYSTEM)로 정렬.
- `NotificationDigestDto.Digest / CategoryCount` — 계약 레코드. `recent`는 안읽은 최신 5건
  (`NotificationDto` 재사용), `summary`는 사람이 읽는 한 줄.
- `NotificationRepository.findSince(...)` — 창 시작 이후 알림 최신순, `Pageable`로 500건 상한.
- `NotificationService.digest(userId, window)` — 엔티티 → `Entry` 사영 후 집계기 호출.

### 프론트

- `useNotificationDigest(24)` 쿼리(60s 폴링) + `NotificationDigest` 타입.
- 설정 알림센터에 **`알림 요약` 카드** 추가(조용한 시간 카드 아래): 요약 문장 · 분류별 칩
  (라벨·건수·안읽음 배지) · 헤더 안읽음 배지. 모바일 360px에서 겹침 없음.
- `mockAdapter`에 `/notifications/digest` 핸들러 + `buildNotificationDigest`(백엔드 계약 동형).
- `smoke.mjs`에 `notification-digest` 체크 추가(설정 방문 → `알림 요약`·`최근 24시간` 문구 확인,
  라이브 `GET /notifications/digest` 4xx/5xx는 기존 리스너가 포착).

## 발견한 블로커 수정 — V25 (R42 잔여 스키마 드리프트)

클린 DB로 부팅하려다 **선행 버그**를 발견해 최소 수정했다(R45 검증을 막던 실블로커).

- **증상:** 신규 DB에서 `ddl-auto=validate`가
  `notification_prefs.quiet_end_hour` `int2(SMALLINT) ≠ int4(INTEGER)`로 거부 → SessionFactory
  생성 실패로 부팅 불가.
- **원인:** V24가 시각 컬럼을 `SMALLINT`로 만들었으나 엔티티는 `int quietStartHour/quietEndHour`
  (→ Hibernate INTEGER). 기존 환경엔 이미 맞는 컬럼이 있어 잠복해 있던 드리프트.
- **수정:** `V25__notification_prefs_hour_int.sql` — 두 시각 컬럼을 `INTEGER`로 확장(값 0-23은
  그대로 보존). 앱 전역이 `int`(DTO `@Min/@Max`, 서비스 시그니처)라 DB를 넓히는 쪽이 무파장.

## 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- **단위 `NotificationDigestTest` 5/5 통과**(분류 유도, 집계·고정순서, recent 최신 5·READ 제외,
  빈 창 요약, 요약 문장). 순수 단위(DB 불필요). ※ 한글+공백 경로 `gradlew test` 워커 인코딩
  버그 회피 위해 **ASCII 경로 복사본(C:\vein_be)에서 실행 → BUILD SUCCESSFUL**.
- 프론트 `typecheck`/프로덕션 `build` 통과.
- **라이브 검증**(docker compose infra + bootRun + `next start`):
  - V25 적용 확인(`Migrating schema ... to version "25"`, `Started VeinApplication`).
  - 빈 다이제스트: 200, snake_case 계약(`window_hours`·`generated_at`·`categories`·`recent`·
    `summary`), 요약 `최근 24시간 새 알림이 없습니다.`(코드포인트 검증 — 콘솔 글리프 깨짐은 표시
    문제일 뿐 데이터 정상 UTF-8).
  - 채워진 다이제스트(표본 3건 주입): `total=3 unread=2`, 카테고리 고정순서
    `SIGNAL(1,1)·SCANNER(1,1)·LIQUIDATION(1,0)`, `recent=[신호,조건검색]`(READ 제외·최신순).
  - **라이브 Chrome smoke 0에러**(신규 `notification-digest` 체크 포함). `알림 요약` 카드가 조용한
    시간과 알림 규칙 사이에 정상 렌더(스크린샷 육안 확인).

### 검증 메모(로컬 환경)

- 인프라는 docker compose(postgres:16 + redis:7), 이번에 새 볼륨으로 기동돼 위 V25 드리프트가
  표면화됐다.
- `backend/.env`의 `SEED_ADMIN_PASSWORD`가 V22 회전 해시(20자)와 불일치(자격 드리프트)해 시드
  관리자 로그인 불가. 검증은 R35 공개 회원가입으로 임시 유저를 만들어 진행했고(smoke 위해
  임시로 SUPER_ADMIN 승격), **검증 후 주입 알림·임시 유저·리프레시토큰을 모두 삭제**해 DB를
  원복했다. 별개 이슈로 남김 — 코드가 아니라 로컬 `.env` 값의 문제.
