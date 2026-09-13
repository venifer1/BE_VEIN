# VEIN Round 139

Date: 2026-09-13

## 백테스트 과적합 경고 규칙 단위 테스트 (BE, 회귀 보호)

자율 루프 R139. 워크포워드 과적합 경고(기획서 §11) 순수 규칙 `BacktestService.isOverfit`에
테스트 추가. 규칙: (IS 평균수익>0 && OOS 평균수익<0) 또는 (OOS 승률 < IS 승률 − 15%p)면
과적합, 한쪽이라도 0거래면 비교 불가(false).

### 바뀐 것

- `BacktestService.isOverfit(in, out)`를 package-private static으로 변경(순수).
- `BacktestOverfitTest` (5): 유사성과 no·승률 16%p↓ yes·정확히 15%p no(strict)·수익 반전 yes·
  0거래 no.

### 검증

ASCII 경로 복사본 `gradlew test --tests BacktestOverfitTest` **그린**. 로직 무변경(가시성만).
</content>
