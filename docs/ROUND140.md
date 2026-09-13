# VEIN Round 140

Date: 2026-09-13

## 라이브 가격 스토어 단위 테스트 (FE, 회귀 보호)

자율 루프 R140. 프로덕션 실시간 WS 피드가 쓰는 `livePrices` 스토어의 `setMany` 병합/필터
로직에 테스트 추가(zustand 바닐라 스토어 직접 호출).

### 바뀐 것 (프론트, 테스트만)

- `store/livePrices.test.ts` (6): 유효행 저장, 무효행(빈 심볼·null/빈 price) 스킵, 기존 맵
  병합(타 심볼 보존), ts 폴백(row.ts→batch ts→null)·change_rate 기본 null, 빈 배열 no-op,
  setConnected 토글.

### 검증

`vitest run` **47/47 그린**(format 24·types 8·api 4·mockData 5·livePrices 6). `tsc` 통과.
소스 무변경.
</content>
