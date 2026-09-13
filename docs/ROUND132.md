# VEIN Round 132

Date: 2026-09-13

## 시장 국면 판정 로직 단위 테스트 (BE, 회귀 보호)

자율 루프 R132. R37 거시경제 엔진의 국면 판정 순수 함수 `MacroService.label`
(BULL/BEAR/RANGE/TRANSITION)이 테스트가 없었다. 이 라벨은 홈 국면 배너의 근거라 회귀
보호가 중요. `private static` → package-private로 열고 테스트.

### 바뀐 것

- `MacroService.label`을 package-private static으로 변경(순수·무의존, 테스트 위해).
- `src/test/java/com/vein/macro/MacroServiceTest.java` (6): score≥2 BULL, score≤-2 BEAR,
  밴드 내 강세·약세 혼재 TRANSITION, 한쪽만 있으면 RANGE, 빈 신호는 점수 무관 RANGE,
  score/signals/summary 보존.

### 검증

ASCII 경로 복사본에서 `gradlew test --tests MacroServiceTest` **그린**. 로직 무변경(가시성만
확대).
</content>
