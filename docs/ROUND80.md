# VEIN Round 80

Date: 2026-09-12

## IMALOL 신호에 C 예상가 거리 노출 (FE, R66/R67 확장)

자율 루프 R80. 홈·스캐너의 신호 카드와 신호 상세는 R66/R67에서 **현재가→C 목표까지 거리(%)**
와 손익비(R:R)를 보여주지만 **ABC/TOP 전용**이었다. 그런데 현재 활성 신호의 큰 축이 IMALOL이고,
IMALOL도 탐지 시 항상 `c_target`을 갖는다(`SignalDetectionService`에서 `projectedClose` =
이말올 박스 투영가로 채움). 그래서 IMALOL 카드/상세에는 목표가 아예 안 보였다. 이 갭을 메웠다.

### 바뀐 것 (FE 전용)

- `signal-card.tsx` — C목표 노출 조건에 `IMALOL` 추가. 카드는 기존과 동일하게
  "C {가격} (±X%)"로 현재가 대비 거리를 계산·표시(부호 처리 이미 일반화돼 있음).
- `app/signals/[id]/page.tsx` — 상세 헤더 목표가 표시에 `IMALOL` 추가하되 **라벨 구분**:
  ABC/TOP = "C 목표가"(c100 피벗 목표), IMALOL = "C 예상가"(투영가). 후자는 Explain의
  "C 예상가에 도달한 뒤 지지 확인" 문구와 용어를 맞췄다.
- 손익비(R:R) 블록은 **ABC/TOP 유지** — IMALOL은 저가-이탈식 무효화가(`invalidation_price`)가
  없어(null) 무효화까지 거리·R:R를 계산할 기준이 없기 때문. 목표 거리만 노출한다.
- `lib/types.ts` — `Signal.c_target` 주석을 `ABC/TOP: C 목표가, IMALOL: C 예상가(투영가)`로 갱신.

### 검증

- 백엔드/계약 무변경. mock은 이미 IMALOL 신호에 `c_target`이 있어 패리티 유지(신규 없음).
- `tsc --noEmit` 통과, 프로덕션 `next build` 통과.
- 실측: `GET /signals/30`(IMALOL) → `current=2512000, c_target=2511000, invalidation=null`
  → 상세 "C 예상가 2,511,000" 표시·R:R 블록 미표시(무효화 null) 정합.
- FE 재기동 후 라이브 Chrome full smoke **0에러**.
