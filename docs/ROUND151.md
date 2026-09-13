# VEIN Round 151

Date: 2026-09-13

## 데모 공포·탐욕 히스토리 생성기(getFearGreedHistory) 불변식 테스트 (FE, 회귀 보호)

자율 루프 R151. R145에서 라벨 분류(`classifyFg`)는 덮었으나, 그 라벨을 붙이는
시계열 생성기 `getFearGreedHistory`(시드 난수로 SSR/CSR 일치 보장) 자체는 무테스트였다.
길이·클램프·결정성·정렬 불변식을 못박음.

### 바뀐 것

- `lib/mockData.test.ts` +4(`getFearGreedHistory` 기존 export 사용, 코드 변경 0):
  - `days` 개수 반환(기본 30, 7 지정).
  - 모든 값 [5,95] 클램프.
  - **시드 결정성**: 두 번 호출해도 값 배열 동일(SSR/CSR 하이드레이션 불일치 방지).
  - 날짜 오름차순(오래된→최신) 정렬.

### 검증

FE `npx vitest run` **7파일 86개 그린**(R150 82 → +4). `mockData` 순수부 누적 18개.
