# VEIN Round 190

Date: 2026-09-13

## 데모 신호 성과(getSignalPerformance) 조회/분기 단위 테스트 (FE, 회귀 보호)

자율 루프 R190. 데모(mock) 신호 성과 상세 `getSignalPerformance`(전체 id 또는 숫자
접미사로 조회, 갓 탐지된 DETECTED 신호는 지평 미평가)는 무테스트였다. mockData 상세
getter 4종(파생·틱띄기·전략·테마·TVL에 이어) 마지막 커버리지 보강.

### 바뀐 것

- `lib/mockData.test.ts` +4(`getSignalPerformance`·`signals` 기존 export 사용, 코드 변경 0):
  - 미존재 id → null.
  - **전체 id / 숫자 접미사** 둘 다로 조회("sig_001"↔"001").
  - 비-fresh(NEAR_COMPLETION) → 5개 지평(1h~7d).
  - too-fresh(DETECTED + 짝수 id) → 빈 지평 배열.

### 검증

FE `npx vitest run` **8파일 114개 그린**(R189 110 → +4).
