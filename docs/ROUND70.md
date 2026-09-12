# VEIN Round 70

Date: 2026-09-12

## 알림 분류 마커 공유 상수화 (드리프트 방지, 품질)

자율 루프 R70. 알림 다이제스트(R45) 분류는 제목 마커(`"Scanner match:"`, `"Liquidation spike"`)로
카테고리를 유도하는데, 이 문자열이 **생성부와 분류부에 각각 리터럴로 중복**돼 있었다. 한쪽만
바꾸면 분류가 조용히 깨지는 잠복 결합.

### 바뀐 것

- `NotificationDigest`에 공유 상수 `SCANNER_MATCH_PREFIX`·`LIQUIDATION_SPIKE_PREFIX` 추가,
  `category()`가 이를 사용.
- 생성부가 같은 상수를 참조: `ConditionScannerService`(조건검색 매칭 알림 제목),
  `LiquidationService`(청산 급증 알림 제목). 이제 한 곳만 바꿔도 생성·분류가 함께 이동.
- **단위 테스트** `categoryMarkersUseSharedConstants`: 상수로 만든 제목이 SCANNER/LIQUIDATION으로
  분류되는지 고정.

### 검증

- 백엔드 `compileJava`/`compileTestJava` 성공. **`NotificationDigestTest` 8/8**(기존 7 + 상수 결합 1).
  ASCII 경로 통과.
- 동작 동일(제목 문자열 byte-identical). bootRun 정상, **Chrome smoke 0에러**. FE/계약 무변경.
