# VEIN Round 109

Date: 2026-09-13

## 펀딩차익 기대수익 계산 단위 테스트 (BE, 회귀 보호)

자율 루프 R109. R108에서 드러난 "expected_1x/2x = 펀딩 징수 횟수(count)" 의미를 코드로
고정해두기 위해 `FundingService.expectedProfitPct`(순수 static)에 단위 테스트 신설.
테스트가 없어 라벨/의미 드리프트를 막지 못했던 갭을 메운다.

### 바뀐 것

- `src/test/java/com/vein/funding/FundingServiceTest.java` (6 케이스):
  - 1회 = pct×1 − 0.21(왕복수수료), 2회 = pct×2 − 0.21.
  - 회수↑ → 기대수익↑, 음수 펀딩은 수수료 차감 후 음수 가능.
  - count 음수는 0으로 클램프(수수료만 남음), null 펀딩 → null.
  - 주석에 "1x/2x = 레버리지 아님, 펀딩 징수 횟수" 명시(R107 재발 방지).

### 검증

ASCII 경로 복사본에서 `gradlew test --tests FundingServiceTest` **그린**. 코드 무변경(테스트만).
왕복수수료 0.21% = 2×(Upbit 0.05% + Bybit 0.055%) 확인.
</content>
