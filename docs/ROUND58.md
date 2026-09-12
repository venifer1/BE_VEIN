# VEIN Round 58

Date: 2026-09-12

## 조건검색 검증의 null 필드 500 누출 수정 (실측 발견)

자율 루프 R58. 신규-유저/잘못된-바디 실측 프로브에서 **`POST /scanner/rules` 및 `/scanner/run`에
빈/부분 바디(`{}`)를 보내면 400이 아니라 500**이 나는 것을 발견했다.

### 원인

`ConditionScannerService.validate()`가 `Set.of(...).contains(x)`로 화이트리스트를 검사하는데,
**immutable `Set.of(...)`는 `contains(null)` 호출 시 NullPointerException을 던진다**(JDK 규약).
market/logic/indicator/operator가 null이면(예: `{}` 바디) `MARKETS.contains(null)` 등에서 NPE →
catch-all → 500. R43이 파라미터 검증을 4xx로 정규화했지만 이 바디-검증 경로의 null은 빠져 있었다.

### 수정 (`ConditionScannerService`)

- `market == null || !MARKETS.contains(market)` — null 먼저 거름.
- `logic == null || !Set.of("AND","OR").contains(logic)`.
- `validateCondition`: indicator/operator를 먼저 null 체크한 뒤 `INDICATORS`/`OPERATORS.contains`
  호출. 모두 null이면 `INVALID_FILTER`(400).

### 검증

- 백엔드 `compileJava` 성공.
- **라이브**(재프로브): `POST /scanner/rules {}`·`/scanner/run {}` → **400**(이전 500),
  indicator 누락·`logic:"XOR"` → 400, 유효 요청 → 200. **Chrome smoke 0에러**.
- FE/계약 무변경(에러 봉투 형태 동일).

### 메모

- 같은 패턴(`Set.of(...).contains(사용자입력)`)이 다른 곳에도 있으면 동일 위험 — 이번엔 스캐너
  검증 경로만 확인·수정. 후속 라운드에서 전역 grep로 추가 점검 여지.
