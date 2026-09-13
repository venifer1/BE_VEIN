# VEIN Round 168

Date: 2026-09-13

## 웹푸시(VAPID) 순수 헬퍼 단위 테스트 (FE, 회귀 보호)

자율 루프 R168. 백엔드 연속 라운드에서 FE로 전환. 백그라운드 웹푸시의 순수 헬퍼
(`lib/web-notifications.ts`)가 무테스트였다: `notificationIds`(알림 id 문자열화)·
`urlBase64ToUint8Array`(VAPID base64url→바이트)·`subscriptionMatchesKey`(서버 키 회전 감지).

### 바뀐 것

- 신규 `lib/web-notifications.test.ts` (+6, 코드 변경 0):
  - `notificationIds`: id 문자열화, 빈 배열.
  - `urlBase64ToUint8Array`: 패딩 없는 base64url 디코드("aGVsbG8"→hello 바이트),
    `-`/`_` 치환+재패딩("-_w"→[251,252]).
  - `subscriptionMatchesKey`: 저장 키 없음→false, 현재 공개키 바이트 일치→true,
    길이/바이트 불일치(키 회전)→false.
- node 환경엔 `window`가 없어 `window.atob`만 지연 참조하는 헬퍼용으로 얇은
  `globalThis.window ??= globalThis` shim을 테스트 상단에 추가.

### 검증

FE `npx vitest run` **8파일 96개 그린**(R152 이후 FE 90 → +6). 작성 중 base64url 수기 디코드
기대값 오류 1건(`w`=48 오산 → [251,255]로 적음)을 테스트 실패로 즉시 잡아 [251,252]로 수정.
