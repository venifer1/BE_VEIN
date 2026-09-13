# VEIN Round 185

Date: 2026-09-13

## 데모 전략 실행 히스토리(getStrategyHistory) 정렬/캡 단위 테스트 (FE, 회귀 보호)

자율 루프 R185. 백엔드 순수부가 얇아져 FE로 전환. 데모(mock) 전략 성과 히스토리
`getStrategyHistory`(run_at 내림차순 + limit 캡)는 무테스트였다.

### 바뀐 것

- `lib/mockData.test.ts` +3(`getStrategyHistory`·`strategies` 기존 export 사용, 코드 변경 0):
  - 시드 전략 런을 run_at 내림차순으로 반환(최신 38.7 → 오래된 21.4).
  - limit 캡(2)은 최신 2건 유지(전체 slice(0,2)와 동일).
  - 미존재 전략 id → 빈 배열.

### 검증

FE `npx vitest run` **8파일 99개 그린**(R184 96 → +3).
