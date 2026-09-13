# VEIN Round 111

Date: 2026-09-13

## Timeframe 지원 매트릭스·코드파싱 단위 테스트 (BE, 회귀 보호)

자율 루프 R111. `Timeframe`은 (시장, 봉) 유효 조합의 단일 진실 소스인데 테스트가 없었다.
주식은 1d/3d/1w만, 코인은 인트라데이 전체 — 이 세트가 조용히 바뀌면 캔들/스캔 엔드포인트가
깨진다. 회귀 보호로 고정.

### 바뀐 것

- `src/test/java/com/vein/common/TimeframeTest.java` (6 케이스):
  - `fromCode` 대소문자 무시(4h==4H), 무효 코드 → `UNSUPPORTED_TIMEFRAME`.
  - duration 매핑(4h=4시간, 1w=7일, 1M=30일).
  - 주식(US/KOSPI/KOSDAQ)은 D1/D3/W1만 지원, H1/M15/MN1 미지원.
  - 코인은 M15~MN1 전체, null/미지 시장은 코인으로 폴백.

### 검증

ASCII 경로 복사본에서 `gradlew test --tests TimeframeTest` **그린**. 코드 무변경(테스트만).
</content>
