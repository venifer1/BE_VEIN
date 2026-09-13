# VEIN Round 178

Date: 2026-09-13

## 백테스트 horizon 파서 예외/관용 경로 보강 (BE, 회귀 보호)

자율 루프 R178. `BacktestService.parseHorizon`은 정상 단위(1d/4h/2w/12)만 테스트돼 있고,
잘못된 입력 거부(400 게이트)와 대소문자·공백 관용은 무테스트였다.

### 바뀐 것

- 기존 `BacktestSimulationTest` +2(코드 변경 0):
  - 대소문자·공백 관용: "1D"→1일, " 3 h "→3시간.
  - 잘못된 입력 거부(IllegalArgumentException): null·공백·"abc"(무매칭)·"1x"(허용 단위
    아님)·"0d"(0 이하).
- 관찰: `HORIZON_PATTERN`이 단위를 `[hdwHDW]?`로 제한하므로 switch의 "bad unit" default는
  실질 도달 불가(정규식에서 먼저 걸림) — "1x"류는 무매칭 경로로 거부됨.

### 검증

ASCII 경로 복사본 `gradlew test --tests BacktestSimulationTest` **BUILD SUCCESSFUL**.
코드 무변경.
