# VEIN Round 126

Date: 2026-09-13

## toChartTime·formatTime 단위 테스트 (FE, 회귀 보호)

자율 루프 R126. 남은 순수 헬퍼 `toChartTime`(차트 시간축 epoch초 변환)·`formatTime`
(null/무효 처리) 회귀 보호 추가.

### 바뀐 것 (프론트, 테스트만)

- `lib/format.test.ts`: toChartTime(ISO→epoch초, Date.UTC 교차검증), formatTime(null→"-",
  빈문자→"-", 무효 iso→passthrough).

### 검증

`vitest run` **26/26 그린**(format 14·types 8·api 4). `tsc` 통과. 소스 무변경.

_비고: FE 순수 헬퍼(format/types/api) 커버리지가 거의 완비됨. 남은 미테스트 로직은 WS 스토어·
mock 어댑터 등 목킹이 필요한 것들뿐. 웹/BE 폴리시·테스트 avenue도 소진 단계 — 실질 다음
단계는 보류 트랙(Flutter/결제)._
</content>
