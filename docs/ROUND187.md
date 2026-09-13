# VEIN Round 187

Date: 2026-09-13

## 데모 TVL 히스토리(getTvlHistory) 불변식 단위 테스트 (FE, 회귀 보호)

자율 루프 R187. 데모(mock) 디파이 TVL 상세 차트 `getTvlHistory`(엔티티 id 시드로 90일
시계열 생성, 미존재 id는 null)는 무테스트였다.

### 바뀐 것

- `lib/mockData.test.ts` +3(`getTvlHistory`·`tvlProtocols` 기존 export 사용, 코드 변경 0):
  - 미존재 엔티티 id → null.
  - 알려진 엔티티 → 90포인트·이름 일치·날짜 오름차순.
  - **시드 결정성**: 동일 id 두 번 호출 시 tvl 값 배열 동일(SSR/CSR 일치).

### 검증

FE `npx vitest run` **8파일 105개 그린**(R186 102 → +3).
