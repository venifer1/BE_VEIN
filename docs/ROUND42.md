# VEIN Round 42

Date: 2026-09-08

## 조용한 시간 (Quiet Hours) — 알림 노이즈 완화 (Track A #3)

PROJECT_STATUS §4 Track A #3의 남은 마찰 항목 **알림 노이즈**를 다룬다. 알림은 쿨다운만
있을 뿐 시간대 제어가 없어, 자는 사이에도 새 신호마다 알림이 쌓였다. 사용자가 지정한
시간대(KST)에는 새 알림을 만들지 않게 한다.

### 설계 결정

- **억제 = 생성 스킵.** 조용한 시간 창에서는 `AlertEvaluationService`가 in-app 알림을
  아예 만들지 않는다(웹푸시는 앱 실행 중 폴링이라 생성이 곧 노출). 신호 자체는 스캐너·홈
  "오늘의 주목 신호"에 남아, 창이 끝난 뒤 확인 가능 — 3시에 밀린 핑을 몰아 보지 않는다.
- **쿨다운 미진전.** 창 동안 억제된 알림은 쿨다운을 진전시키지 않아, 창이 끝나고 온 후속
  신호가 정상적으로 알림을 낸다.
- **자정을 넘는 창 지원.** `start>end`(예: 22→8)는 밤을 가로지르는 창으로 해석. `start==end`면
  창 없음(안전).
- **안전 폴백.** 설정 조회 실패/미설정은 "억제 안 함" — 알림을 조용히 잃지 않는다.

### 추가된 것

**백엔드 (`com.vein.notification`)**
- V24 `notification_prefs`(user_id PK, quiet_enabled, quiet_start_hour, quiet_end_hour, updated_at).
- `NotificationPref` 엔티티 + `NotificationPrefRepository`.
- `NotificationPrefService` — get/upsert + `isQuietNow(userId, now)`(KST, wrap 지원, never throw).
- `NotificationPrefController` — `GET/PUT /api/v1/me/notification-prefs`.
- `AlertEvaluationService.onSignalCreated` — 쿨다운 통과 후 `isQuietNow`면 `continue`(생성 스킵).

**프론트 (FE_VEIN)**
- 설정 "조용한 시간" 카드: 토글 + 시작/종료 시각(00:00~23:00 선택), start==end 경고.
- `useNotificationPrefs`/`useUpdateNotificationPrefs` 훅, mock `/me/notification-prefs` GET/PUT.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공(JDK21).
- 프론트 `tsc --noEmit` 0에러, 프로덕션 `build` 성공.
- **라이브 Chrome smoke(mock) 전 라우트 0에러.** 설정에 "조용한 시간" 카드 + 토글 On 시
  시각 선택 노출 — 스크린샷 확인. 검증 후 실데이터로 재빌드 복원.

### 남은 것 / 주의

- `isQuietNow` wrap 로직은 순수 함수라 단위 테스트 적합하나, 한글 경로에서 `gradlew test`가
  워커 인코딩 버그로 실패해 이번엔 compile 검증만(로직은 단순). ASCII 경로에서 테스트 권장.
- 조용한 시간은 **생성 자체를 스킵**하므로 창 중 신호 알림은 사후에도 없다(신호는 피드에 존재).
  "창 중엔 조용히 쌓아두고 창 후 요약"으로 바꾸려면 별도 스풀링 설계 필요 — 후속 여지.
