# VEIN Round 191

Date: 2026-09-13

## 데모 신호 상세(getSignalDetail) 조회/유효기간/알고버전 단위 테스트 (FE, 회귀 보호)

자율 루프 R191. mockData 상세 getter의 마지막 미커버 함수 `getSignalDetail`(접두사 관용
조회, 유효기간 = 탐지+30일, 패턴 타입별 algorithm_version)에 테스트 추가.

### 바뀐 것

- `lib/mockData.test.ts` +3(`getSignalDetail`·`signals` 기존 export 사용, 코드 변경 0):
  - 미존재 id → null.
  - 전체 id/숫자 접미사 조회 + **유효기간 = detected_at + 30일**(R85, 결정적).
  - 패턴 타입별 algorithm_version(ABC→abc-java-1.0.0, TOP→top-…, IMALOL→imalol-…).

### 검증

FE `npx vitest run` **8파일 117개 그린**(R190 114 → +3). mockData 상세 getter 전수 커버
완료(신호상세/성과·파생·틱띄기·전략·테마·TVL).
