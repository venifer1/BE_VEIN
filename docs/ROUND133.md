# VEIN Round 133

Date: 2026-09-13

## 경제 캘린더 순수 헬퍼 단위 테스트 (BE, 회귀 보호)

자율 루프 R133. R39 경제 캘린더의 NFP(매월 첫째 금요일) 산출·D-day 계산 순수 함수에
테스트 추가. 캘린더 배지/이벤트 리스크의 기반이라 회귀 보호.

### 바뀐 것

- `EconomicCalendarProvider.firstFriday`·`dday`를 package-private로 열고 테스트.
- `src/test/java/com/vein/macro/EconomicCalendarProviderTest.java` (4): 첫째 금요일(월요일
  시작·금요일 시작 케이스), 12개월 전부 1~7일 내 금요일, dday 부호(0·+3·-2).

### 검증

ASCII 경로 복사본에서 `gradlew test --tests EconomicCalendarProviderTest` **그린**. 로직
무변경(가시성만 확대).
</content>
