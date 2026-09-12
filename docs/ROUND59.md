# VEIN Round 59

Date: 2026-09-12

## 다이제스트 기간 토글(24시간/7일) + 일수 라벨 (FE + BE 폴리시)

자율 루프 R59. R58에서 `Set.of(...).contains(null)` 위험을 전역 grep으로 추가 점검했으나
스캐너 외 사이트는 모두 null-가드/필수 path-variable로 안전함을 확인(무수정). 대신 다이제스트
기간을 고를 수 있게 실사용성을 높였다. (백엔드는 이미 `?window=`를 지원했으나 FE는 24h 고정.)

### 바뀐 것

- **백엔드** `NotificationDigest.summary`: 창 길이 라벨을 `windowLabel()`로 분리 — **24 초과 &
  24의 배수면 "최근 N일"**(168→"최근 7일"), 그 외 "최근 N시간"(24→"최근 24시간" 유지). 요약
  문구가 7일 창에서 "최근 168시간"으로 어색해지는 것을 방지.
- **프론트** 설정 "알림 요약" 카드: **24시간/7일 토글** 추가(`useNotificationDigest(windowHours)`).
  헤더 문구를 기간 중립으로 정리.
- `smoke.mjs` 다이제스트 체크를 토글 라벨(항상 존재하는 "24시간"·"7일") 기준으로 견고화.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `NotificationDigestTest` 7/7**(기존 6 +
  7일 라벨 1). ASCII 경로 통과.
- 프론트 `typecheck`/`build` 통과.
- **라이브**: `?window=24`→"최근 24시간", `?window=168`→"최근 7일"(코드포인트 확인).
  **Chrome smoke 0에러**.

### R59 실측 메모

넓은 엣지-입력 프로브(bad enum/param, 음수·초대형 limit/days/window, 잘못된 주문 바디, 잘못된
피드백 사유, 비정상 이메일 가입)에서 **500 없음** — 전부 400/404/200 클램프로 견고. R58이 마지막
남은 500 누출이었던 것으로 보임.
