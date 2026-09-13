# VEIN Round 197

Date: 2026-09-13

## Pattern Score 하위점수 분기(PatternScoreCalculator) 단위 테스트 (BE, 회귀 보호)

자율 루프 R197. Pattern Score v1(완성도30·거래량20·추세20·변동성10·뉴스20)의 상위
`calculate`는 총점 3케이스만 있었고, 각 하위점수의 분기(추세 충족개수·변동성 밴드·뉴스
스텝/클램프)는 미검증이었다. private 하위메서드를 노출하지 않고 `calculate` 시나리오로
격리 검증.

### 바뀐 것

- 기존 `PatternScoreCalculatorTest` +3(다른 하위점수는 null 중립으로 격리, 코드 변경 0):
  - **추세**: 4개 불리시 체크 중 2개 → 20×2/4=10, 0개 → 0.
  - **변동성**: 스윗스팟[1,6]→만점10, 인접밴드[0.5,1)→0.6배 6, 밴드밖(20)→0.2배 2.
  - **뉴스**: 중립10·step5에서 +1→15·-1→5·+3→25 클램프 20.

### 검증

ASCII 경로 복사본 `gradlew test --tests PatternScoreCalculatorTest` **BUILD SUCCESSFUL**
(3→6 @Test). 코드 무변경.
