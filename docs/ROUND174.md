# VEIN Round 174

Date: 2026-09-13

## 주식 등락률 계산 추출 + 단위 테스트 (BE, 리팩터+회귀 보호)

자율 루프 R174. 주식 movers의 전일 대비 등락률 `(close/prevClose - 1) * 100`이
`equitySnapshot` 루프에 인라인되어 테스트 불가였다(R165 김치 프리미엄과 같은 패턴).
순수 함수로 **추출**(동작 보존)하고 테스트 추가.

### 바뀐 것

- `MoversService.changePct(close, prevClose)` 순수 static 추출(소수 4자리 HALF_UP, 무효
  입력이면 null). 루프의 가드(close/prevClose null·prevClose 0 → skip)를 null 반환으로
  동일 매핑 → **동작 무변경**. 루프는 `changePct==null이면 continue`로 단순화.
- 신규 `MoversServiceTest` (+4): 상승(+10.0000)·하락(-10.0000)·불변(0.0000)·
  4자리 반올림(-66.6667)·null 입력.

### 검증

ASCII 경로 복사본 `gradlew test --tests MoversServiceTest` **BUILD SUCCESSFUL**.
리팩터는 순수 추출이라 계산 결과 동일.
