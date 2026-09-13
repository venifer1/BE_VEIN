# VEIN Round 186

Date: 2026-09-13

## 데모 테마 구성종목(getThemeConstituents) 조회 단위 테스트 (FE, 회귀 보호)

자율 루프 R186. 데모(mock) 테마(섹터) 상세 `getThemeConstituents`(테마 존재 시 구성종목
목록, 미매핑 테마는 빈 목록, 미존재 id는 null)는 무테스트였다.

### 바뀐 것

- `lib/mockData.test.ts` +3(`getThemeConstituents` 기존 export 사용, 코드 변경 0):
  - 존재 테마(301) → theme_id·name·구성종목 2건(NVDA 등) 매핑.
  - 구성종목 없는 테마(305 미분류) → 빈 items 배열(테마 자체는 non-null).
  - 미존재 테마 id(999) → null.

### 검증

FE `npx vitest run` **8파일 102개 그린**(R185 99 → +3).
