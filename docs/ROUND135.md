# VEIN Round 135

Date: 2026-09-13

## R77 실질 무효화가 실측 예시 회귀 고정 (BE, 테스트)

자율 루프 R135. `SignalStatusTransitionService.thresholdPrice`(실질 무효화가 = 기준선 ×
(1−buffer))는 이미 테스트가 있었으나(97·100·null·음수), R77 문서에 남긴 **실측 예시
(/signals/275: 89.25 → 86.5725)**를 명시적으로 고정해 반올림 드리프트를 막았다.

### 바뀐 것 (테스트만)

- `SignalLowBreakTest.thresholdPriceIsLineMinusBuffer`에 assertion 추가:
  `thresholdPrice(89.25, 0.03) == 86.5725`.

### 검증

ASCII 경로 복사본 `gradlew test --tests SignalLowBreakTest` **그린**. 소스 무변경.
</content>
