# VEIN Round 60

Date: 2026-09-12

## 엔타이틀먼트에 현재 사용량(used) 추가 (Track C)

자율 루프 R60. R52/R56에서 티어 한도(저장식 3·알림 10)를 넣고 구독 카드에 한도를 표시했지만
**현재 사용량이 없어**, 사용자가 402를 맞기 전까지 "얼마나 남았는지" 알 수 없었다. 각 기능에
현재 사용 수를 실어 게이트를 투명하게 만든다.

### 바뀐 것

- **백엔드**: `EntitlementsDto.Feature`에 `used` 추가. `Entitlements.forTier(tier, scannerUsed,
  alertUsed)` 오버로드(기존 무인자 버전은 0 위임). `EntitlementsService.entitlements`가
  `ScannerRuleRepository.countByUserId`·`AlertRepository.countByUserId`로 실사용을 채운다.
  (게이트에서 이미 쓰던 카운트 재사용, 새 쿼리 없음.)
- **프론트**: 구독 카드가 한도 대신 **"사용/한도"**(예: `2 / 3개`, PRO는 `N / 무제한`)를 표시하고,
  한도 도달 시 앰버 강조. mock도 `used`(저장식/알림 수) 반영.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **단위 `EntitlementsTest` 7/7**(기존 6 + 사용량 반영 1).
  ASCII 경로 통과.
- 프론트 `typecheck`/`build` 통과.
- **라이브**: `GET /me/entitlements` → `SAVED_SCANNER_RULES used=0 limit=3`, `ALERTS used=0
  limit=10`. **Chrome smoke 0에러**.
