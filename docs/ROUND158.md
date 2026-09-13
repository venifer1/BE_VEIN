# VEIN Round 158

Date: 2026-09-13

## 조건검색 거래량비율(ConditionScannerService.volumeRatio) 단위 테스트 (BE, 회귀 보호)

자율 루프 R158. 조건검색기 지표 `VOLUME_RATIO`의 산출식 `volumeRatio`(최신 봉 거래량 ÷
직전 최대 20봉 평균)는 스캐너 매칭에 직접 쓰이는데 테스트가 없었다. R135~R139에서
compare/matchRate/frequencyGrade는 덮었으므로 같은 서비스의 마지막 순수 산출식 보강.

### 바뀐 것

- `ConditionScannerService.volumeRatio`: `private` → package-private `static`(동작 무변경).
- 기존 `ConditionScannerServiceTest` +4:
  - 최신/직전평균(직전 3봉 100, 현재 200 → 2.0000).
  - 현재 거래량 null·직전 표본 0(단일 봉) → null.
  - **20봉 창 상한**: index0 거대값은 창 밖 → 제외(20봉 평균 100, 현재 300 → 3.0000).
  - 4자리 반올림(100/3 → 33.3333).

### 검증

ASCII 경로 복사본 `gradlew test --tests ConditionScannerServiceTest` **BUILD SUCCESSFUL**
(7→11 @Test). 로직 무변경(가시성만 확장).
