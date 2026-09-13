# VEIN Round 153

Date: 2026-09-13

## 유통량 비율 계산(SupplyService.circulatingPct) 단위 테스트 (BE, 회귀 보호)

자율 루프 R153. 최근 FE mock 테스트가 이어졌기에 백엔드로 전환. 유통량 탭
(supply_tab.py 이식)의 핵심 순수 계산 `circulatingPct(circulating, max, total)`는
이미 package-private static인데 테스트가 없었다.

### 바뀐 것

- 신규 `SupplyServiceTest` (+5, 코드 변경 0):
  - max>0이면 max를 분모로(18M/21M=85.7143%).
  - max 없거나 0이면 total로 폴백(50/200=25.0000, max=0 → total 100 → 50.0000).
  - circulating null → null.
  - 사용 가능한 분모 없음(max·total 둘 다 null/0) → null.
  - 데이터 이상 시 100% 초과도 그대로 표현(250/200=125.0000).

### 검증

ASCII 경로 복사본 `gradlew test --tests SupplyServiceTest` **BUILD SUCCESSFUL**. 로직 무변경.
