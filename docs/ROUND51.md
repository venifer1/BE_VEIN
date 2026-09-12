# VEIN Round 51

Date: 2026-09-12

## 알림 다이제스트에 "조용한 시간 방출" 요약 (Track A #3 후속)

R46 스풀링은 조용한 시간에 온 알림을 `held_until`까지 보류했다가 창이 끝나면 노출한다. R51은
"자는 사이 보류됐다 방금 도착한 게 몇 건인지"를 R45 다이제스트에서 한눈에 보여준다. 별도
스케줄러/알림 생성 없이 **읽기 시점 집계**만 확장한다.

### 바뀐 것 (`com.vein.notification`)

- `NotificationDigest.Entry`에 `heldUntil` 추가, `summarize`가 `released` = (heldUntil != null) 건수를
  집계. 다이제스트는 이미 `findSince`가 `held_until <= now`만 통과시키므로, heldUntil이 있는 행은
  곧 "보류됐다 방출된" 항목이다.
- `NotificationDigestDto.Digest`에 `released` 필드 추가.
- `NotificationService.digest`가 `n.getHeldUntil()`을 Entry에 매핑.

### 프론트

- `NotificationDigest` 타입에 `released`. 설정 "알림 요약" 카드에 `released > 0`이면
  "조용한 시간에 보류됐다 방금 도착: N건" 라인 표시. mock은 스풀 데이터가 없어 `released=0`.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `NotificationDigestTest` 6/6**(기존 5 +
  released 집계 1). ASCII 경로 **BUILD SUCCESSFUL**.
- 프론트 `typecheck`/`build` 통과.
- **라이브**: 보류-방출 알림 1건 + 일반 1건 주입 → `GET /notifications/digest` `total=2 released=1`
  확인. **Chrome smoke 0에러**. (스풀 데이터 없는 스모크 유저는 released=0이라 라인 미표시 — 정상)
