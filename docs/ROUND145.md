# VEIN Round 145

Date: 2026-09-13

## 공포·탐욕 지수 라벨 분류 경계값 단위 테스트 (FE, 회귀 보호)

자율 루프 R145. 오프라인 데모(mock)의 공포·탐욕(Fear & Greed) 히스토리가 0~100 값을
5단계 라벨(Extreme Fear/Fear/Neutral/Greed/Extreme Greed)로 나누는 `classifyFg`는
경계값 구간(≤24/≤44/≤55/≤74)이 많아 실수하기 쉬운데 테스트가 없었다.

### 바뀐 것

- `lib/mockData.ts`: `classifyFg`를 export(순수, 부수효과 없음).
- `lib/mockData.test.ts` +1(10단언): 각 경계 양쪽(0·24·25·44·45·55·56·74·75·100)에서
  라벨 전이 확인.
- 코드 로직 변경 없음(가시성만 확장).

### 검증

FE `npx vitest run` **6파일 56개 그린**(R144 55 → +1).
