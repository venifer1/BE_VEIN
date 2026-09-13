# VEIN Round 142

Date: 2026-09-13

## 주간 리포트 집계 헬퍼 단위 테스트 (BE, 회귀 보호)

자율 루프 R142. 공개 주간 리포트(R34) "전체 가중 집계"의 빌딩블록 순수 함수
`recoverHits`(반올림 hit_rate+표본→적중수 복원)·`pct`(가중 재계산)·`signed`(부호 표기)에
테스트 추가.

### 바뀐 것

- 위 3개를 package-private로 변경(순수).
- `WeeklyReportServiceTest` +3 (총 6): recoverHits(60%×10=6·61.9%×42→26·null/0), pct(60.0·33.3·
  0.0), signed(+1.61·-0.42·null/빈→0.00).

### 검증

ASCII 경로 복사본 `gradlew test --tests WeeklyReportServiceTest` **그린**. 로직 무변경.
</content>
