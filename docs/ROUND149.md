# VEIN Round 149

Date: 2026-09-13

## 데모 딥링크 경로 id 매칭(idMatches) 단위 테스트 (FE, 회귀 보호)

자율 루프 R149. 데모(mock) 어댑터가 `/instruments/{id}` 등 경로 파라미터를 엔티티에
매칭할 때 쓰는 `idMatches`는 정확 일치 + "접두사_숫자"(예 `usr_2`↔`2`) 완화 매칭을
하는데, 이 규칙(특히 접두사 한 번만 제거·대소문자 구분)에 테스트가 없었다.

### 바뀐 것

- `lib/mockAdapter.ts`: `idMatches` export(순수, 로직 무변경).
- `lib/mockAdapter.test.ts` +5:
  - 정확 일치(문자열·숫자·`usr_2` 자기 자신).
  - 선행 알파 접두사 1개 제거 후 일치(`usr_2`→`2`, `paper_1`→`1`).
  - 다른 id 불일치.
  - **중첩 접두사는 첫 `alpha_`만 제거**(`abc_def_2`→`def_2`≠`2` → false).
  - 밑줄 없으면 미제거·대소문자 구분(`BTC`↔`btc` false).

### 검증

FE `npx vitest run` **7파일 75개 그린**(R148 70 → +5). `mockAdapter` 순수부 누적 11개.
