# VEIN Round 137

Date: 2026-09-13

## 조건검색 빈도 등급/매치율 단위 테스트 (BE, 회귀 보호)

자율 루프 R137. 조건검색 시뮬레이션(R24)의 매치율(%)·빈도 등급(LOW/MEDIUM/HIGH) 순수
계산을 테스트. package-private로 열어 회귀 보호.

### 바뀐 것

- `ConditionScannerService.matchRate`·`frequencyGrade`를 static package-private로 변경(순수).
- `ConditionScannerServiceTest`에 6케이스 추가: matchRate(0 가드·5.00·33.33 HALF_UP),
  frequencyGrade(<2 LOW·2 MEDIUM·<10 MEDIUM·10 HIGH 경계).

### 검증

ASCII 경로 복사본 `gradlew test --tests ConditionScannerServiceTest` **그린**. 로직 무변경.
</content>
