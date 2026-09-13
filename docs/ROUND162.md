# VEIN Round 162

Date: 2026-09-13

## 공개 캔들 DTO 매핑(CandleDto.from) 단위 테스트 (BE, 회귀 보호)

자율 루프 R162. 차트/캔들 API가 반환하는 `CandleDto.from(Candle)`은 BigDecimal을
plain string으로(계약: 숫자는 String), 시각을 ISO-8601 UTC로 매핑하는데 테스트가 없었다.

### 바뀐 것

- 신규 `CandleDtoTest` (+3, 코드 변경 0):
  - 전 필드 plain string 매핑 + openTime은 `TimeUtil.toIso` 위임 확인.
  - **지수표기 방지**: `toPlainString`으로 0.00000001이 `1E-8`이 아닌 `0.00000001`.
  - null volume은 null 유지(NPE 없이).

### 검증

ASCII 경로 복사본 `gradlew test --tests CandleDtoTest` **BUILD SUCCESSFUL**. 코드 무변경.
