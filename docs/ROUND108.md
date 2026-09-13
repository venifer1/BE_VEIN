# VEIN Round 108

Date: 2026-09-13

## 펀딩차익 라벨 정정 — R107 오류 되돌림 ("펀딩 1회/2회") (FE)

자율 루프 R108. **R107에서 "기대 1회/2회"를 "기대 1배/2배"(레버리지)로 바꾼 것은 오류였다.**
백엔드 `FundingService.expectedProfitPct(fundingPct, count)` = `fundingPct × count − 왕복수수료`
확인 결과, exp1은 count=1, exp2는 count=2 — 즉 **펀딩 징수 횟수**(1회/2회)이지 레버리지 배수가
아니다. 원래 "회"가 맞았고 R107이 의미를 왜곡했다.

### 바뀐 것 (프론트 전용)

- 펀딩차익 카드: R107의 "기대 1배/2배" → **"펀딩 1회/2회"**로 정정(원래 의미 복원 + "펀딩"을
  붙여 무엇을 세는지 명확화).

### 교훈

용어를 "쉽게" 바꾸기 전에 **실제 계산 의미를 코드로 확인**해야 한다. R107은 이 검증을
생략해 오해를 오히려 심었다. (근거: `FundingService:147` expectedProfitPct = pct×count−fee,
`FundingService:133-134` exp1=count1·exp2=count2.)

### 검증

`tsc`·`next build` 통과. 백엔드 계산과 라벨 의미 일치 확인.
</content>
