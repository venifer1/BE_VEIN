# VEIN Round 148

Date: 2026-09-13

## 데모 어댑터 순수 헬퍼(초성 검색·숫자 표기) 단위 테스트 (FE, 회귀 보호)

자율 루프 R148. 오프라인 데모(mock)의 종목 검색은 한글 초성 매칭을 지원하는데
(`toCho`·`isChoQuery`), 숫자 표기 헬퍼(`num`·`trimNum`)와 함께 `mockAdapter.ts` 안에
비공개로 묻혀 테스트가 없었다. 신규 `mockAdapter.test.ts`로 회귀 보호.

### 바뀐 것

- `lib/mockAdapter.ts`: `toCho`·`isChoQuery`·`num`·`trimNum` export(순수, 로직 무변경).
- 신규 `lib/mockAdapter.test.ts` +6:
  - `toCho`: 한글→초성("비트코인"→"ㅂㅌㅋㅇ"), 비한글·공백·빈문자 통과.
  - `isChoQuery`: 전부 초성일 때만 true(전체 음절·라틴·혼합·빈문자 false).
  - `num`: null/undefined/"" → 0, 숫자열 파싱.
  - `trimNum`: 후행 0·잔여 소수점 제거(2→"2", 1.5→"1.5", 1.23456789 유지).
- **`mockAdapter.ts`가 node 테스트 환경에서 부작용 없이 import됨을 확인**(어댑터 자체는
  호출 안 함, 순수 헬퍼만 검증).

### 검증

FE `npx vitest run` **7파일 70개 그린**(R147 64 → +6).
