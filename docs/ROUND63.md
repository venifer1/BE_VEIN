# VEIN Round 63

Date: 2026-09-12

## 알림 "모두 읽음" (편의 갭)

자율 루프 R63. 알림함은 **개별 읽음만** 있고 일괄 읽음이 없었다. 청산 급증 알림(R30)이 승인
사용자 전체에 브로드캐스트돼 안읽음이 잘 쌓이는데, 하나씩 읽는 건 번거롭다.

### 추가

- **백엔드** `POST /api/v1/notifications/read-all` → `{updated}`. `NotificationRepository.markAllRead`
  (modifying UPDATE): 활성 안읽음만 READ+readAt 세팅. **스풀링(R46) 보류 중(held_until>now)은
  건드리지 않음**(활성 안읽음 정의와 일치).
- **프론트** 설정 알림함 헤더에 안읽음>0일 때 "모두 읽음" 버튼(`useMarkAllRead`). mock
  `/notifications/read-all` 핸들러.

### 검증

- 백엔드 `compileJava` 성공. 프론트 `typecheck`/`build` 통과.
- **라이브**: 안읽음 2 주입 → `read-all` `updated=2` → 안읽음 **0**. **Chrome smoke 0에러**.
