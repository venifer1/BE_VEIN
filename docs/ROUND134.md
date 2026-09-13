# VEIN Round 134

Date: 2026-09-13

## 이벤트 리스크 D-day 라벨 단위 테스트 (BE, 회귀 보호)

자율 루프 R134. R39 `EventRiskService.ddayLabel`(D-DAY/D-N/D+N) 순수 함수에 테스트 추가.
(전체 assess는 캘린더·실적 provider 의존이라 순수 라벨만 대상.)

### 바뀐 것

- `EventRiskService.ddayLabel`을 package-private로 열고 테스트.
- `src/test/java/com/vein/macro/EventRiskServiceTest.java` (1): D-DAY(0)·D-7·D-1·D+2.

### 검증

ASCII 경로 복사본에서 `gradlew test --tests EventRiskServiceTest` **그린**. 로직 무변경.
</content>
