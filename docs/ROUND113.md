# VEIN Round 113

Date: 2026-09-13

## 관리자 사용자 상태 배지 한글화 (FE, 용어 일관성)

자율 루프 R113. 관리자 화면의 사용자 상태 배지가 raw enum(APPROVED/LOCKED/PENDING)이라
앱의 다른 한글 라벨과 어긋났다. 관리자도 사용자(SUPER_ADMIN)이므로 한글화.

### 바뀐 것 (프론트 전용)

- `admin` `UserStatusBadge`: `USER_STATUS_LABEL` 추가 — APPROVED→**승인됨**, LOCKED→**잠김**,
  PENDING→**승인 대기**. variant 색 로직은 유지.

### 검증

`tsc`·`next build` 통과, mock 빌드(3100) full smoke **0에러**. 관리자 캡처로 사용자 관리
배지 "승인됨/승인 대기/잠김" 렌더 확인. 실데이터 빌드로 원복.

_후속(R114 후보): 관리자 "사용자 상태"·"신호 상태" 분포 차트의 raw enum(APPROVED/
NEAR_COMPLETION 등)도 한글화._
</content>
