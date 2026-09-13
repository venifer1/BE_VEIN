# VEIN Round 159

Date: 2026-09-13

## Explain 거래량비율(SignalExplainService.volumeRatio) 단위 테스트 (BE, 회귀 보호)

자율 루프 R159. R157에서 같은 서비스의 `averageRangePct`는 덮었고, Explain 근거 문구
"최근 거래량이 직전 평균보다 N배"에 쓰이는 `volumeRatio`(최신봉 ÷ 나머지 전체 평균,
소수 2자리)는 아직 무테스트였다. (조건검색기 volumeRatio(R158)는 20봉 상한·4자리로 별개.)

### 바뀐 것

- `SignalExplainService.volumeRatio`: `private` → package-private `static`(동작 무변경).
- 기존 `SignalExplainServiceTest` +4:
  - 최신÷나머지평균(200 / avg[100,100,100]=100 → 2.00).
  - 표본 부족(size<2)·최신봉 거래량 null → null.
  - 나머지 전부 null → null.
  - **2자리 반올림**(100/3 → 33.33) — 조건검색기 4자리(33.3333)와 다름을 명시.

### 검증

ASCII 경로 복사본 `gradlew test --tests SignalExplainServiceTest` **BUILD SUCCESSFUL**
(4→8 @Test). 로직 무변경(가시성만 확장).
