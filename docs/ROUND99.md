# VEIN Round 99

Date: 2026-09-13

## 스캐너 필터 패턴 라벨 단일 소스화 (FE, 품질)

자율 루프 R99. `signal-filter-bar`가 패턴 타입 라벨(ABC/고점되돌림/이말올)을 카드 배지의
`SIGNAL_TYPE_LABEL`(badges.tsx)과 **별도로 하드코딩**하고 있었다 — 한쪽만 바뀌면 필터와
카드 라벨이 어긋나는 드리프트 위험(R70에서 다룬 상수 중복과 같은 유형).

### 바뀐 것 (프론트 전용)

- `signal-filter-bar`의 `TYPES`가 `SIGNAL_TYPE_LABEL.{ABC,TOP,IMALOL}`를 재사용하도록 변경.
  라벨 단일 소스화(동작 동일).

### 검증

`tsc`·`next build` 통과. 표시 라벨 값은 기존과 동일(고점되돌림·이말올), 소스만 통합.
</content>
