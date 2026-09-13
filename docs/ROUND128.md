# VEIN Round 128

Date: 2026-09-13

## mock 성과요약 필터 단위 테스트 (FE, 오프라인 데모 보호)

자율 루프 R128. 오프라인 데모(mock)에서 실서버 `/signals/performance/summary`를 흉내내는
`getSignalPerformanceSummary` 필터 로직에 테스트 추가.

### 바뀐 것 (프론트, 테스트만)

- `lib/mockData.test.ts` (5): 기본 horizon 1d·무필터 전체, type 필터, type+timeframe 정확일치,
  market 필터, 없는 horizon/type→빈배열.

### 검증

`vitest run` **33/33 그린**(format 16·types 8·api 4·mockData 5). `tsc` 통과. 소스 무변경.
</content>
