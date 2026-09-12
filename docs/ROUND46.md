# VEIN Round 46

Date: 2026-09-12

## 알림 스풀링 — 조용한 시간 보류 후 방출 (Track A #3 마무리)

R42 "조용한 시간"은 창 동안 알림을 **드롭**했다(생성 자체를 건너뜀 → 영구 소실). R46은
드롭 대신 **보류(spool)** 한다: 창 동안 도착한 알림은 `held_until`(창 종료 시각)까지 읽기
모델에서 제외돼 핑·배지가 뜨지 않고, 창이 끝나면 **자연히 노출**된다(lazy-release). 자는
사이 핑은 줄이되 **알림을 놓치지 않는다** — R42의 순수한 손실을 보완한다.

### 설계 — lazy-release (스케줄러·플래그 플립 없음)

알림 하나가 "활성"인 조건: `held_until IS NULL OR held_until <= now`. 방출을 위한 배치
작업이 필요 없다 — 읽기 시점에 시각만 비교하면 창이 끝나는 순간 자동으로 나타난다. 경쟁
상태·플립 누락이 원천적으로 없다. FE(`WebNotifications`)는 이미 unread 목록을 폴링해 새 항목만
브라우저 알림으로 띄우므로, 서버가 보류 알림을 목록·안읽음에서 빼두면 창 종료 후 다음 폴링에
정확히 한 번 도착한다.

### 추가·변경 (`com.vein.notification`, `com.vein.alert`)

- **V26** `notification_prefs`… 아니라 `notifications`에 `held_until TIMESTAMPTZ NULL` +
  부분 인덱스(`held_until IS NOT NULL`). NULL = 즉시 활성(기존 알림 전부).
- `Notification.heldUntil` 필드(+builder).
- `NotificationRepository`:
  - `findPage`·`findSince`에 `(held_until IS NULL OR held_until <= :now)` 필터 + `now` 파라미터.
  - `countActiveUnread(userId, now)` — 보류 중은 안읽음에서 제외.
- `NotificationService`:
  - `list()`가 `now`를 넘기고 안읽음 수를 `countActiveUnread`로 계산.
  - `createInApp(...)`에 `heldUntil` 오버로드 — 보류면 웹푸시 전달을 **건너뛴다**(IN_APP 생성
    기록은 남김). 기존 5-인자 시그니처는 `heldUntil=null`로 위임(하위호환).
  - `digest()`가 `now`를 `findSince`에 전달 → 보류 중은 다이제스트에서도 제외.
- `NotificationPrefService`:
  - `windowEnd(now, start, end)` — 순수 정적. `now`(KST)가 창 안이면 다음 `end:00` KST 순간을,
    밖이면 null. 자정을 넘는 창 지원.
  - `quietWindowEnd(userId, now)` — 설정 조회 + `windowEnd`. throw 없음.
- `AlertEvaluationService`: 조용한 시간에 `continue`(드롭)하던 것을 **`quietWindowEnd`로 계산한
  `heldUntil`로 보류 생성**으로 교체. 보류도 실제 알림이므로 쿨다운을 진전(R42는 미진전 후
  재발화였음 — 이제 중복 없이 한 번만).

### 프론트

- 설정 "조용한 시간" 카드 문구를 실제 동작에 맞게 정정: "이 시간대(KST)에 온 알림은
  보류됐다가 창이 끝나면 도착합니다." (기존 "새 알림을 만들지 않습니다"는 이제 부정확).
- 계약 변화 없음(보류 항목은 서버에서 제외되므로 클라이언트는 방출 후에만 봄) → mock 무변경.

## 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- **단위 `NotificationSpoolWindowTest` 6/6**(창 밖→null, 빈 창→null, 정상창 당일 종료, 자정넘김
  저녁→익일 아침, 자정넘김 새벽→당일 아침, 시작시각 포함) + 기존 `NotificationDigestTest` 5/5
  회귀 통과. 순수 단위(DB 불필요), ASCII 경로 복사본에서 **BUILD SUCCESSFUL**.
- 프론트 `typecheck`/프로덕션 `build` 통과.
- **라이브 검증**(bootRun + V26 적용 확인 + `next start`):
  - 표본 주입 — 보류(미래)·해제(과거)·일반(NULL) 3건:
    - `GET /notifications` → `[Normal, Released past]`만, `unread_count=2` (보류 미래 제외).
    - `GET /notifications/digest?window=24` → `total=2 unread=2` (보류 미래 제외).
    - 보류 항목 `held_until`을 과거로(창 종료 시뮬레이션) → `GET /notifications`에 3건 전부,
      `unread_count=3` (**lazy-release 확인**).
  - **라이브 Chrome smoke 0에러**. 설정 "조용한 시간" 카드 정정 문구 렌더 스크린샷 육안 확인.
  - 검증 후 주입 알림·임시 유저·refresh_tokens 삭제로 DB 원복.

### 메모

- 로컬 검증은 R45와 동일 제약(시드 관리자 자격 드리프트 → R35 공개 회원가입으로 임시 유저 생성,
  smoke 위해 임시 SUPER_ADMIN 승격 후 원복). 상세는 ROUND45.md 참고.
- 남은 Track A #3: 차트 조작감. (알림 노이즈 축: 신호 큐레이션 R41 · 조용한 시간 R42 ·
  다이제스트 R45 · **스풀링 R46**으로 일단락)
