# VEIN Round 161

Date: 2026-09-13

## 신호 멱등키(PatternSignal.buildSignalKey) 단위 테스트 (BE, 회귀 보호)

자율 루프 R161. 신호 중복 방지의 근간인 멱등키(§10.1)
`buildSignalKey(algorithmVersion, ruleId, instrumentId, timeframe, anchorCandleTime)`는
`signal_key` UNIQUE 제약과 직결되는 public static인데 테스트가 없었다.

### 바뀐 것

- 신규 `PatternSignalTest` (+3, 코드 변경 0):
  - 파이프 구분 포맷(`v1|abc-rule|42|1d|2026-09-13T00:00:00Z`, anchor는 Instant ISO-8601).
  - 동일 입력 결정성.
  - **필드별 구분성**: algorithmVersion·ruleId·instrumentId·timeframe·anchor 중 하나만
    달라도 키가 달라짐(중복 신호 오탐/누락 방지).

### 검증

ASCII 경로 복사본 `gradlew test --tests PatternSignalTest` **BUILD SUCCESSFUL**. 코드 무변경.
