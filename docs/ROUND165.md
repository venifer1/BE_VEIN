# VEIN Round 165

Date: 2026-09-13

## 김치 프리미엄 계산 추출 + 단위 테스트 (BE, 리팩터+회귀 보호)

자율 루프 R165. 김치 프리미엄 `(업비트가 / (바이낸스가 × 환율) − 1) × 100`이 refresh
스케줄러 루프 안에 인라인되어 있어 테스트 불가였다. 순수 함수로 **추출**(동작 보존)하고
테스트 추가.

### 바뀐 것

- `KimchiPremiumService.premiumPct(upbitPrice, binancePrice, usdkrw)` 순수 static 추출
  (소수 4자리 HALF_UP, 무효 입력이면 null). 기존 루프의 가드(업비트/바이낸스 null·
  바이낸스≤0·환산 원화≤0 → skip)를 함수 null 반환으로 동일 매핑 → **동작 무변경**.
  루프는 `premiumPct(...)==null이면 continue`로 단순화.
- 신규 `KimchiPremiumServiceTest` (+5):
  - 정프(1400/1000-1=40.0000)·역프(-10.0000)·패리티(0.0000).
  - 4자리 반올림(1/3 기반 -66.6667).
  - null·바이낸스 0/음수 → null.

### 검증

ASCII 경로 복사본 `gradlew test --tests KimchiPremiumServiceTest` **BUILD SUCCESSFUL**.
리팩터는 순수 추출이라 계산 결과 동일.
