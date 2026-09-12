# VEIN Round 68

Date: 2026-09-12

## 한도 게이트 판정 중앙화 + 테스트 (Track C 품질)

자율 루프 R68. 최근 FE 폴리시가 이어져 백엔드 품질을 손봤다. FREE 한도 게이트(R52 저장식·R56
알림)가 두 서비스에 **`limit >= 0 && count >= limit`로 인라인 중복**돼, 경계(`>` vs `>=`)를 한쪽만
바꾸면 조용히 어긋날 위험이 있었다.

### 바뀐 것

- `Entitlements.overLimit(int limit, long currentCount)` 순수 헬퍼 추가 — 무제한(-1)이면 false,
  아니면 `count >= limit`. `AlertService.create`·`ConditionScannerService.save` 둘 다 이걸 쓰도록
  교체(동작 동일, 중복 제거).
- **단위 테스트** `EntitlementsTest.overLimitBoundary`: -1 무제한→false, 여유→false, 정확히 한도→
  true, 초과→true, 한도 0→true.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **`EntitlementsTest` 8/8**(기존 7 + 경계 1).
  ASCII 경로 통과.
- **라이브 회귀**: 스캐너 게이트 저장 1~3→201, 4번째→**402**(리팩터 전과 동일). **Chrome smoke
  0에러**. FE/계약 무변경.
