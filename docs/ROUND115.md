# VEIN Round 115

Date: 2026-09-13

## TimeUtil ISO 라운드트립·freshness 임계 단위 테스트 (BE, 회귀 보호)

자율 루프 R115. `TimeUtil.freshness`는 전 화면 "최신/지연" 배지를 결정하는 로직(경과가
2×봉 초과 시 DELAYED)인데 테스트가 없었다. ISO 라운드트립(UTC/Z)과 함께 회귀 보호 추가.

### 바뀐 것

- `src/test/java/com/vein/common/TimeUtilTest.java` (5 케이스):
  - `toIso`/`parseIso` null-safe + 라운드트립(Z 접미사).
  - freshness: null 수집시각 → DELAYED, 2×봉 이내 → FRESH, 2×봉 초과 → DELAYED,
    정확히 2×봉 경계 → FRESH(초과만 지연, 경계 배타적).

### 검증

ASCII 경로 복사본에서 `gradlew test --tests TimeUtilTest` **그린**. 코드 무변경(테스트만).
</content>
