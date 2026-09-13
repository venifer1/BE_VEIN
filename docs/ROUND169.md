# VEIN Round 169

Date: 2026-09-13

## 지수 히스토리 다운샘플링(MarketIndexService.downsample) 단위 테스트 (BE, 회귀 보호)

자율 루프 R169. 시장 지수 히스토리 차트를 위한 다운샘플링 `downsample(points, max)`
(균등 간격 추출 + 최신점 항상 포함)은 인덱스 산술이 미묘한데 테스트가 없었다.

### 바뀐 것

- `MarketIndexService.downsample`: `private static` → package-private `static`(동작 무변경).
- 신규 `MarketIndexServiceTest` (+4):
  - n ≤ max이면 입력 그대로 반환(동일 인스턴스).
  - n=10, max=5 → 균등 인덱스 0·2·4·6 + 마지막 9.
  - 큰 입력(100→7)에서도 첫점(0)·마지막점(99) 항상 포함.
  - max=2면 첫점·마지막점만.

### 검증

ASCII 경로 복사본 `gradlew test --tests MarketIndexServiceTest` **BUILD SUCCESSFUL**.
로직 무변경(가시성만 확장).
