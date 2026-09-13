# VEIN Round 189

Date: 2026-09-13

## 데모 틱띄기 상세(getScalpDetail) 호가창 불변식 단위 테스트 (FE, 회귀 보호)

자율 루프 R189. 데모(mock) 틱띄기 상세 `getScalpDetail`(5호가 오더북 생성, 미존재 심볼
null)은 무테스트였다. 호가 정렬(매도 오름/매수 내림)·스프레드 불변식을 못박음.

### 바뀐 것

- `lib/mockData.test.ts` +2(`getScalpDetail`·`scalpRanking` 기존 export 사용, 코드 변경 0):
  - 미존재 심볼 → null.
  - 5호가 반환·심볼 일치, **각 레벨 매도호가>매수호가**, 매도 오름차순·매수 내림차순.

### 검증

FE `npx vitest run` **8파일 110개 그린**(R188 108 → +2).
