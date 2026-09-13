# VEIN Round 188

Date: 2026-09-13

## 데모 파생 상세(getDerivativeDetail) 조회/불변식 단위 테스트 (FE, 회귀 보호)

자율 루프 R188. 데모(mock) 파생(선물) 상세 `getDerivativeDetail`(심볼 대소문자 무시 조회,
48시간 롱숏비·OI 시계열 생성, 미존재 심볼 null)은 무테스트였다.

### 바뀐 것

- `lib/mockData.test.ts` +3(`getDerivativeDetail`·`derivatives` 기존 export 사용, 코드 변경 0):
  - 미존재 심볼 → null.
  - 대소문자 무시 조회, 롱숏/OI 48포인트, **롱숏비 0.4 하한** 불변식.
  - 시드 결정성: 동일 심볼 두 번 호출 시 롱숏비 배열 동일.

### 검증

FE `npx vitest run` **8파일 108개 그린**(R187 105 → +3).
