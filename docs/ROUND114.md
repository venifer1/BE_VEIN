# VEIN Round 114

Date: 2026-09-13

## 관리자 분포 차트 상태 라벨 한글화 (FE, R113 후속)

자율 루프 R114. 관리자 개요의 "사용자 상태"·"신호 상태" 분포 차트가 raw enum
(APPROVED/PENDING/LOCKED, NEAR_COMPLETION/DETECTED/INVALIDATED/EXPIRED)을 그대로 노출했다.
R113(사용자 상태 배지)에 이어 분포 차트도 한글화.

### 바뀐 것 (프론트 전용)

- `Distribution`에 `labels?` prop 추가(`labels?.[key] ?? key`).
- "사용자 상태" ← `USER_STATUS_LABEL`(승인됨/잠김/승인 대기), "신호 상태" ← badges의
  `STATUS_LABEL`(탐지/완성 임박/실패/만료/종료). badges `STATUS_LABEL`을 export해 단일 소스 재사용.

### 검증

`tsc`·`next build` 통과. 라벨 매핑만(집계·API 무변경). 신호상태 라벨은 카드/배지와 동일 소스.
</content>
