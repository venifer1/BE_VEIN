# VEIN Round 150

Date: 2026-09-13

## 데모 어댑터 요청 파싱(parseUrl·body) 단위 테스트 (FE, 회귀 보호)

자율 루프 R150. 데모(mock) 어댑터의 모든 라우트가 통과하는 진입 파서
`parseUrl`(경로/쿼리 분리·`config.params` 병합)·`body`(요청 본문 JSON 파싱)는
전 요청 처리의 근간인데 테스트가 없었다.

### 바뀐 것

- `lib/mockAdapter.ts`: `parseUrl`·`body` export(순수, 로직 무변경).
- `lib/mockAdapter.test.ts` +7:
  - `parseUrl`: 경로/쿼리 분리·후행 슬래시 제거(`/a/b///`→`/a/b`), `config.params`
    병합 시 undefined/null/빈문자 스킵+문자열화, 동일 키는 params가 쿼리 덮어씀,
    url 누락·빈 쿼리 처리.
  - `body`: JSON 문자열 파싱, 잘못된 JSON·무데이터·빈문자 → `{}`, 객체 본문 그대로 통과.

### 검증

FE `npx vitest run` **7파일 82개 그린**(R149 75 → +7). `mockAdapter` 순수부 누적 18개.
