# VEIN Round 154

Date: 2026-09-13

## 신호 성과 수익률 계산(SignalPerformanceService.pct) 단위 테스트 (BE, 회귀 보호)

자율 루프 R154. 신호 성과 추적의 핵심 공식 `pct(value, base) = (value/base - 1) * 100`은
return_pct·MFE·MAE 세 지표가 모두 공유하는데 테스트가 없었다. `this`를 쓰지 않고
상수(HUNDRED·PCT_SCALE)가 이미 static이므로 package-private static으로 열어 테스트.

### 바뀐 것

- `SignalPerformanceService.pct`: `private` → package-private `static`(동작 무변경).
- 신규 `SignalPerformanceServiceTest` (+4):
  - 상승/하락(110/100=+10.0000, 90/100=-10.0000).
  - value==base → 0.0000.
  - 4자리 반올림(1/3 → -66.6667).
  - value null·base null·base 0 → null.

### 검증

ASCII 경로 복사본 `gradlew test --tests SignalPerformanceServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
