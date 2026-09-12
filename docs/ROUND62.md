# VEIN Round 62

Date: 2026-09-12

## 알림 규칙 삭제 (실질 기능 갭)

자율 루프 R62. `alerts`는 목록/생성/수정(enable·cooldown)만 있고 **삭제가 아예 없었다**. FREE는
알림 10개 한도(R56)인데 지울 방법이 없어 **한도에 갇히고** 오래된 규칙이 쌓인다 — 실사용 갭.

### 추가

- **백엔드** `DELETE /api/v1/alerts/{id}` + `AlertService.delete(userId, id)`:
  - 소유자만. 없거나 남의 것이면 **404**(존재 여부 미노출, IDOR-safe).
  - FK(`notifications.alert_id REFERENCES alerts(id)`, cascade 없음) 때문에 삭제 전
    `NotificationRepository.clearAlertId`(UPDATE … SET alert_id=NULL)로 링크를 끊어 FK 위반(500)을
    막는다. **알림 이력은 보존**(V14와 동일 패턴).
- **프론트**: 설정 알림 규칙 카드에 "삭제" 버튼(확인 후 `useDeleteAlert`). 삭제 시 alerts +
  entitlements(사용량) 갱신. mock `DELETE /alerts/{id}` 핸들러.

### 검증

- 백엔드 `compileJava` 성공. 프론트 `typecheck`/`build` 통과.
- **라이브**: 알림 생성 → `DELETE` **200** → 재삭제 **404** → 목록 **0**. **Chrome smoke 0에러**.
